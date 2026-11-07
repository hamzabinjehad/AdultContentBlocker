// The only tlock surface the vault uses. The bundle is committed so the site
// runs from a static directory with no build step and no network at load time.
import { timelockEncrypt, timelockDecrypt, HttpCachingChain, HttpChainClient, Buffer } from 'tlock-js';

const api = { timelockEncrypt, timelockDecrypt, HttpCachingChain, HttpChainClient, Buffer };
if (typeof window !== 'undefined') window.HisnTlock = api;
export default api;
