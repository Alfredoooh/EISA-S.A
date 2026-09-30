const NodeCache = require('node-cache');

const cache = new NodeCache({
  stdTTL: parseInt(process.env.CACHE_TTL) || 300,
  checkperiod: 60,
  useClones: false
});

function get(key) {
  return cache.get(key);
}

function set(key, value, ttl) {
  if (ttl) {
    cache.set(key, value, ttl);
  } else {
    cache.set(key, value);
  }
}

function del(key) {
  cache.del(key);
}

function flush() {
  cache.flushAll();
}

module.exports = { get, set, del, flush };
