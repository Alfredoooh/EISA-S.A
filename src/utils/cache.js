const NodeCache = require('node-cache');

const cache = new NodeCache({
  stdTTL: Number.parseInt(process.env.CACHE_TTL, 10) || 60,
  checkperiod: 60,
  useClones: false
});

function get(key) { return cache.get(key); }
function set(key, value, ttl) {
  if (Number.isFinite(Number(ttl)) && Number(ttl) > 0) cache.set(key, value, Number(ttl));
  else cache.set(key, value);
}
function del(key) { cache.del(key); }
function flush() { cache.flushAll(); }

module.exports = { get, set, del, flush };
