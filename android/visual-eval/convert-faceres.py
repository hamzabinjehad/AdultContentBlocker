#!/usr/bin/env python3
"""Convert the pinned FaceRes graph model to a native TFLite women/men model.

FaceRes is Human's face description model (HSE-asavchenko/HSE_FaceRec_tf, Apache 2.0), already
pinned and hash-checked in extension/visual/assets.json. This rebuilds the TensorFlow graph from
its TensorFlow.js files (graph JSON plus float16-quantized weights), keeps only the gender head
(output gender_pred/Sigmoid: probability the face is a man), and writes a TFLite model with the
same float16 weights, computed in float32.
It does not download anything and changes no weights.

Requires a Python environment with tensorflow==2.16.2 (numpy comes with it).

  python android/visual-eval/convert-faceres.py            # writes the asset, checks parity
  python android/visual-eval/convert-faceres.py --check    # only re-checks an existing asset

Parity: the TFLite output is compared with the TensorFlow graph on random and structured
inputs; the largest absolute difference must stay under 1e-3 (half-precision compute on CPUs
that support it moves the probability by a few 1e-4).
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
MODEL_JSON = ROOT / "extension/visual/models/faceres.json"
MODEL_BIN = ROOT / "extension/visual/models/faceres.bin"
DEFAULT_OUT = ROOT / "android/app/src/main/assets/faceres_gender.tflite"
OUTPUT = "gender_pred/Sigmoid"
SIZE = 224


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def check_pinned():
    inventory = json.loads((ROOT / "extension/visual/assets.json").read_text())["artifacts"]
    for name, path in (("models/faceres.json", MODEL_JSON), ("models/faceres.bin", MODEL_BIN)):
        if inventory[name]["sha256"] != sha256(path):
            raise SystemExit("FaceRes file differs from the pinned build: " + name)


def graph_def(tf, np):
    from google.protobuf import json_format
    model = json.loads(MODEL_JSON.read_text())
    weights = {}
    data, offset = MODEL_BIN.read_bytes(), 0
    for group in model["weightsManifest"]:
        for spec in group["weights"]:
            count = int(np.prod(spec["shape"])) if spec["shape"] else 1
            quantized = (spec.get("quantization") or {}).get("dtype")
            if quantized == "float16":
                raw = np.frombuffer(data, np.float16, count, offset).astype(np.float32)
                offset += 2 * count
            elif spec["dtype"] in ("float32", "int32"):
                raw = np.frombuffer(data, np.float32 if spec["dtype"] == "float32" else np.int32, count, offset)
                offset += 4 * count
            else:
                raise SystemExit("Unsupported weight encoding: " + json.dumps(spec))
            weights[spec["name"]] = raw.reshape(spec["shape"])
    if offset != len(data):
        raise SystemExit("Weight file length does not match the manifest")

    nodes = []
    for node in model["modelTopology"]["node"]:
        node = json.loads(json.dumps(node))
        node.pop("device", None)
        if node["op"] != "_FusedMatMul":
            nodes.append(node)
            continue
        # _FusedMatMul is a Grappler-only op TFLite cannot read: split it back into
        # MatMul + BiasAdd (+ Relu), keeping the original name on the last node.
        fused = [base64.b64decode(s).decode() for s in node["attr"]["fused_ops"]["list"]["s"]]
        if fused not in (["BiasAdd"], ["BiasAdd", "Relu"]):
            raise SystemExit("Unexpected fused ops: %s" % fused)
        name, (x, kernel, bias) = node["name"], node["input"]
        nodes.append({"name": name + "/matmul", "op": "MatMul", "input": [x, kernel],
                      "attr": {"T": {"type": "DT_FLOAT"}, "transpose_a": node["attr"]["transpose_a"],
                               "transpose_b": node["attr"]["transpose_b"]}})
        add = name + "/bias" if fused == ["BiasAdd", "Relu"] else name
        nodes.append({"name": add, "op": "BiasAdd", "input": [name + "/matmul", bias],
                      "attr": {"T": {"type": "DT_FLOAT"}}})
        if fused == ["BiasAdd", "Relu"]:
            nodes.append({"name": name, "op": "Relu", "input": [add], "attr": {"T": {"type": "DT_FLOAT"}}})
    graph = json_format.ParseDict({"node": nodes}, tf.compat.v1.GraphDef())
    for node in graph.node:
        if node.op == "Const" and node.name in weights:
            value = weights[node.name]
            node.attr["value"].tensor.CopyFrom(tf.make_tensor_proto(value, dtype=value.dtype))
            node.attr["dtype"].type = tf.as_dtype(value.dtype).as_datatype_enum
    return tf.compat.v1.graph_util.extract_sub_graph(graph, [OUTPUT])


def reference(tf, graph):
    def imported(x):
        tf.compat.v1.import_graph_def(graph, input_map={"input_1:0": x}, name="")
        return tf.compat.v1.get_default_graph().get_tensor_by_name(OUTPUT + ":0")
    return tf.compat.v1.wrap_function(imported, [tf.TensorSpec([1, SIZE, SIZE, 3], tf.float32)])


def samples(np):
    rng = np.random.default_rng(7)
    yield rng.uniform(0, 255, (1, SIZE, SIZE, 3)).astype(np.float32)
    ramp = np.linspace(0, 255, SIZE, dtype=np.float32)
    yield np.stack([np.tile(ramp, (SIZE, 1))] * 3, -1)[None]
    yield np.full((1, SIZE, SIZE, 3), 128, np.float32)


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[1])
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    import numpy as np
    import tensorflow as tf
    if tf.__version__ != "2.16.2":
        print("warning: tested with tensorflow 2.16.2, running", tf.__version__, file=sys.stderr)
    check_pinned()
    graph = graph_def(tf, np)
    function = reference(tf, graph)
    if not args.check:
        converter = tf.lite.TFLiteConverter.from_concrete_functions([function], function)
        # The pinned weights are already float16; storing them as float16 loses nothing and halves
        # the asset. TFLite dequantizes them once at load and runs in float32.
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]
        args.out.write_bytes(converter.convert())
    interpreter = tf.lite.Interpreter(model_path=str(args.out))
    interpreter.allocate_tensors()
    input_detail, output_detail = interpreter.get_input_details()[0], interpreter.get_output_details()[0]
    worst = 0.0
    for sample in samples(np):
        expected = function(tf.constant(sample)).numpy()
        interpreter.set_tensor(input_detail["index"], sample)
        interpreter.invoke()
        worst = max(worst, float(np.abs(interpreter.get_tensor(output_detail["index"]) - expected).max()))
    print("input", input_detail["shape"].tolist(), "output", output_detail["shape"].tolist(),
          "max |TFLite - TF| = %.2e" % worst)
    # float16 weights let XNNPACK compute some layers in half precision on CPUs that support it;
    # a probability moving by under 0.001 cannot cross the 0.60 / 0.80 decision thresholds.
    if worst > 1e-3:
        raise SystemExit("TFLite output differs from the TensorFlow graph")
    print("wrote" if not args.check else "checked", args.out, args.out.stat().st_size, "bytes sha256", sha256(args.out))


if __name__ == "__main__":
    main()
