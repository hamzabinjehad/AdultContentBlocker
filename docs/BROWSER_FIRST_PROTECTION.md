# Browser-first protection for mixed-content services

Hisn must not call an entire mixed-content service adult-only, or add its domain
to a block list because one post scores as explicit. Users may keep the website
available and voluntarily restrict its native app, where Hisn cannot inspect
individual posts.

## What the scanner does

- Domain and URL rules remain separate, earlier layers. An intentional site
  block or strict-mode allowance still controls whether the website opens.
- On supported X/Twitter tweet layouts, the scanner scores individual posts'
  rendered text and media descriptions. A positive verdict hides that post and
  pauses its media, without navigating the entire feed to a block page or
  recording its text, URL, matched words or browsing attempt.
- DOM changes, including new posts and recycled nodes, re-arm checking. An old
  response must not hide a different post that has replaced the scored item.
- Other websites retain page-level text scoring. This is not a universal
  per-post adapter for every social service or every future layout.
- Scoring is local and heuristic, using the shared normalizer and signed seed
  vocabulary. There is no server-side browsing or content upload.

Continuous means rechecking while the permitted page and extension run, not
reading every pixel before it can be displayed. Textless images/video, inaccessible
frames, closed shadow content on unsupported browsers and unsupported feed
layouts remain gaps. Worker startup, bounded retries and classification take
time; content can be visible before a verdict. Legitimate discussion can be
misclassified. No visual image/video classifier is implemented.

## Mac setup

Use a supported Chromium browser with Hisn connected and page-text checking
enabled. In **Blocking Rules**, the browser-first guide offers an optional path:
select the native app under **Add app**, save it, and use its website instead.
Do not block the service's domain if its website should remain available.

Mac app rules deny new connections only when the signed system filter is
actually running. They do not prevent the app opening, clear cached/offline
content, or revisit established flows. The development app's ad-hoc signature
is not proof of an eligible, active system filter. Browser guarding and its
recovery countdown are separate from text classification.

## iPhone and iPad setup

The universal app contains **Hisn Text**, a separate Safari Web Extension.
It uses canonical scanner/scorer JavaScript packaged from the same repository;
the existing **Hisn 1–4** declarative domain blockers remain intact.

Enable Hisn Text in Safari's extension settings and explicitly grant access to
the websites to be checked. Reload previously open tabs. Verify profiles and
private browsing separately. The app's three-layer configuration assessment
does **not** verify Hisn Text's website permissions or a successful text block;
the guide labels that access unverified. Apple requires permission for a web
extension to read and change a website. [Apple's permission guidance](https://developer.apple.com/documentation/safariservices/managing-safari-web-extension-permissions).

If the person chooses the browser-first path, use Hisn's activity picker to
select **only the native app**, then **Always block** and confirm. Selecting
the website or a category that includes it can also shield the wanted web
version. Apple keeps selection tokens private; Hisn does not guess tokens from
a service name. Not every native app has a feature-equivalent web version.

The Safari extension does not inspect another browser, another native app or
the user's entire screen. Individual Screen Time authorization remains revocable;
the in-app commitment does not make permission or extension removal impossible.
Phone/Mac settings do not synchronize yet. Properly signed physical iPhone and
iPad acceptance remains necessary; compiled code and Chrome tests cannot prove
Safari behavior on a real phone.

## Acceptance checks

Use synthetic fixtures, not live adult content or user browsing history:

1. A feed containing ordinary and explicit-text fixtures leaves ordinary posts
   readable, hides only the scored post, and does not navigate/block the domain.
2. Insert and edit posts, including equal-length text, continuous changes,
   recycled nodes and removal while a verdict is pending.
3. Verify stale route/content/policy results are discarded, retries are bounded,
   and hidden-item style/media changes do not reveal the scored content.
4. Test benign medical/educational discussion and exact host boundaries.
5. On signed real iPhone and iPad, enable Hisn Text, grant/deny/revoke website
   permission, reload Safari, exercise private/profile settings, and confirm the
   four domain blockers still work independently.
6. Test intentional domain/strict rules and commitment app-selection restrictions
   separately. Preserve essential communication and recovery access.
