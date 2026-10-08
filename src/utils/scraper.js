const axios = require('axios');
const cheerio = require('cheerio');
const { JSDOM } = require('jsdom');
const { Readability } = require('@mozilla/readability');
const { URL } = require('url');
const dns = require('dns').promises;
const net = require('net');

const REQUEST_TIMEOUT = Number(process.env.SCRAPER_TIMEOUT_MS || 18000);
const MAX_HTML_BYTES = Number(process.env.SCRAPER_MAX_HTML_BYTES || 8 * 1024 * 1024);

const NOISE_RE = /(nav|menu|header|footer|sidebar|aside|advert|advertisement|ads?|social|share|related|recommend|recommended|newsletter|subscribe|subscription|comments?|commentary|author-bio|cookie|consent|breadcrumb|pagination|pager|outbrain|taboola|promo|sponsor|login|register)/i;
const ARTICLE_RE = /(article|story|post|content|body|main|entry|news)/i;
const UI_RE = /^(share|follow|subscribe|sign in|log in|read more|more from|related|recommended|advertisement|advert|comments?|cookie|accept|menu|home|next|previous)$/i;

function cleanText(value) {
  return String(value || '')
    .replace(/\u00a0/g, ' ')
    .replace(/[\t ]+/g, ' ')
    .replace(/\r?\n[ \t]+/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

function textFromHtml(html) {
  const $ = cheerio.load(`<div>${html || ''}</div>`);
  $('script, style, noscript, svg, form, nav, footer, header, aside, iframe').remove();
  return cleanText($.root().text());
}

function dedupeParagraphs(paragraphs) {
  const seen = new Set();
  const out = [];
  for (const item of paragraphs) {
    const value = cleanText(item);
    if (!value || UI_RE.test(value) || value.length < 35) continue;
    const key = value.toLowerCase().replace(/[^a-z0-9\u00c0-\u024f]+/gi, ' ').trim();
    if (!key || seen.has(key)) continue;
    seen.add(key);
    out.push(value);
  }
  return out;
}

function extractJsonLd($) {
  const candidates = [];
  $('script[type="application/ld+json"]').each((_, el) => {
    const raw = $(el).contents().text().trim();
    if (!raw) return;
    try {
      const parsed = JSON.parse(raw);
      const stack = Array.isArray(parsed) ? [...parsed] : [parsed];
      while (stack.length) {
        const item = stack.shift();
        if (!item || typeof item !== 'object') continue;
        if (Array.isArray(item['@graph'])) stack.push(...item['@graph']);
        if (Array.isArray(item)) stack.push(...item);
        if (item.articleBody && typeof item.articleBody === 'string') candidates.push({ type: item['@type'], articleBody: item.articleBody, description: item.description, headline: item.headline, datePublished: item.datePublished, author: item.author });
      }
    } catch {
      // JSON-LD inválido não deve impedir a extração por DOM/Readability.
    }
  });
  return candidates.sort((a, b) => String(b.articleBody || '').length - String(a.articleBody || '').length)[0] || null;
}

function extractDomParagraphs($) {
  const selectors = [
    'article',
    '[itemprop="articleBody"]',
    '[class*="article-body"]',
    '[class*="article-content"]',
    '[class*="article__body"]',
    '[class*="story-body"]',
    '[class*="post-content"]',
    '[class*="entry-content"]',
    '[class*="news-body"]',
    'main'
  ];

  let best = [];
  let bestScore = 0;

  for (const selector of selectors) {
    $(selector).each((_, el) => {
      const root = $(el);
      const clone = root.clone();
      clone.find('script,style,noscript,nav,header,footer,aside,form,iframe,svg,[role="navigation"],[aria-label*="share"],[class*="related"],[class*="recommend"],[class*="advert"],[class*="social"],[class*="newsletter"],[class*="comment"]').remove();
      const paragraphs = dedupeParagraphs(clone.find('p').map((__, p) => $(p).text()).get());
      const textLength = paragraphs.join('\n\n').length;
      const score = textLength + paragraphs.length * 120 + (ARTICLE_RE.test(String(root.attr('class') || '')) ? 1000 : 0) - (NOISE_RE.test(String(root.attr('class') || '')) ? 1500 : 0);
      if (paragraphs.length >= 2 && score > bestScore) {
        bestScore = score;
        best = paragraphs;
      }
    });
  }
  return best;
}

async function assertPublicTarget(targetUrl) {
  const url = new URL(targetUrl);
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error('Apenas URLs HTTP/HTTPS são permitidas');
  const host = url.hostname.toLowerCase();
  if (['localhost', 'localhost.localdomain'].includes(host) || host.endsWith('.local') || host.endsWith('.internal')) {
    throw new Error('Destino não permitido');
  }
  if (net.isIP(host)) {
    if (isPrivateIp(host)) throw new Error('Destino privado não permitido');
    return;
  }
  const resolved = await dns.lookup(host, { all: true });
  if (!resolved.length || resolved.some(x => isPrivateIp(x.address))) throw new Error('Destino privado não permitido');
}

function isPrivateIp(ip) {
  if (net.isIPv4(ip)) {
    const [a, b] = ip.split('.').map(Number);
    return a === 10 || a === 127 || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168) || (a === 169 && b === 254) || a === 0;
  }
  const normalized = ip.toLowerCase();
  return normalized === '::1' || normalized === '::' || normalized.startsWith('fc') || normalized.startsWith('fd') || normalized.startsWith('fe80:');
}

async function extractArticle(url) {
  await assertPublicTarget(url);
  const response = await axios.get(url, {
    timeout: REQUEST_TIMEOUT,
    maxContentLength: MAX_HTML_BYTES,
    maxBodyLength: MAX_HTML_BYTES,
    responseType: 'text',
    validateStatus: status => status >= 200 && status < 400,
    headers: {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/128 Safari/537.36 NewsAPI/2.0',
      'Accept': 'text/html,application/xhtml+xml;q=0.9,*/*;q=0.7',
      'Accept-Language': 'en-US,en;q=0.8,pt-PT;q=0.6,pt;q=0.5'
    }
  });

  const finalUrl = response.request?.res?.responseUrl || url;
  const $ = cheerio.load(response.data, { decodeEntities: true });

  const title = cleanText(
    $('meta[property="og:title"]').attr('content') ||
    $('meta[name="twitter:title"]').attr('content') ||
    $('[itemprop="headline"]').first().text() ||
    $('h1').first().text() ||
    $('title').first().text()
  );

  const metaDescription = cleanText(
    $('meta[property="og:description"]').attr('content') ||
    $('meta[name="description"]').attr('content') || ''
  );
  const jsonLd = extractJsonLd($);
  const description = cleanText(jsonLd?.description || metaDescription);

  let image = $('meta[property="og:image"]').attr('content') || $('meta[name="twitter:image"]').attr('content') || '';
  if (!image) image = $('article img').first().attr('src') || $('[itemprop="image"]').first().attr('content') || '';

  const author = cleanText(
    $('meta[name="author"]').attr('content') ||
    $('[itemprop="author"]').first().text() ||
    $('[rel="author"]').first().text() ||
    ''
  ) || (typeof jsonLd?.author === 'string' ? cleanText(jsonLd.author) : cleanText(jsonLd?.author?.name));

  const publishedAt = cleanText(
    $('meta[property="article:published_time"]').attr('content') ||
    $('meta[name="datePublished"]').attr('content') ||
    $('[itemprop="datePublished"]').attr('content') ||
    $('time[datetime]').first().attr('datetime') ||
    jsonLd?.datePublished || ''
  );

  // 1) Schema.org ArticleBody: é a melhor fonte para obter exclusivamente o corpo.
  let body = '';
  let extractionMethod = '';
  if (jsonLd?.articleBody && String(jsonLd.articleBody).length >= 400) {
    body = cleanText(textFromHtml(jsonLd.articleBody));
    extractionMethod = 'json-ld.articleBody';
  }

  // 2) Firefox Reader View / Mozilla Readability.
  if (body.length < 400) {
    try {
      const dom = new JSDOM(response.data, { url: finalUrl });
      const parsed = new Readability(dom.window.document, { charThreshold: 300 }).parse();
      const readerText = cleanText(parsed?.textContent || textFromHtml(parsed?.content || ''));
      if (readerText.length > body.length) {
        body = readerText;
        extractionMethod = 'mozilla-readability';
      }
      dom.window.close();
    } catch (err) {
      console.warn('[Scraper Readability]', err.message);
    }
  }

  // 3) DOM semântico controlado como fallback.
  if (body.length < 400) {
    const paragraphs = extractDomParagraphs($);
    const domText = paragraphs.join('\n\n');
    if (domText.length > body.length) {
      body = domText;
      extractionMethod = 'semantic-dom';
    }
  }

  body = cleanText(body);
  const domain = new URL(finalUrl).hostname.replace(/^www\./, '');

  return {
    url,
    finalUrl,
    title,
    description,
    image: image ? new URL(image, finalUrl).toString() : '',
    author,
    publishedAt,
    source: domain,
    body,
    content: body,
    bodyLength: body.length,
    extractionMethod,
    scrapedAt: new Date().toISOString()
  };
}

module.exports = { extractArticle };
