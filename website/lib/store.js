// Hisn Vault — local persistence and files.
//
// The browser keeps one vault. It is ciphertext, but it is still the only copy
// the person has: the file download is the backup that matters.

const VAULT_KEY = 'hisn.vault.v1';
const PARTNER_KEY = 'hisn.partner.v1';

function read(key) {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function loadVault() {
  return read(VAULT_KEY);
}

export function saveVault(vault) {
  localStorage.setItem(VAULT_KEY, JSON.stringify(vault));
}

export function clearVault() {
  localStorage.removeItem(VAULT_KEY);
}

export function loadPartner() {
  return read(PARTNER_KEY);
}

export function savePartner(partner) {
  localStorage.setItem(PARTNER_KEY, JSON.stringify(partner));
}

export function clearPartner() {
  localStorage.removeItem(PARTNER_KEY);
}

export function vaultFileName(vault) {
  const day = String(vault.createdAt).slice(0, 10);
  return `hisn-vault-${day}.json`;
}

export function partnerFileName(partner) {
  const day = String(partner.createdAt).slice(0, 10);
  return `hisn-partner-key-${day}.json`;
}

export function downloadJson(data, filename) {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

export function readJsonFile(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error('could not read the file'));
    reader.onload = () => {
      try { resolve(JSON.parse(String(reader.result))); } catch { reject(new Error('not json')); }
    };
    reader.readAsText(file);
  });
}

export async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}
