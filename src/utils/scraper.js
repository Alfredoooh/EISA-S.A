const axios = require('axios');
const cheerio = require('cheerio');

async function extractArticle(url) {
  try {
    const response = await axios.get(url, {
      timeout: 10000,
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'pt-PT,pt;q=0.9,en;q=0.8',
      }
    });

    const $ = cheerio.load(response.data);

    // Remove elementos desnecessários
    $('script, style, nav, footer, header, aside, .ads, .advertisement, .social-share, .related-articles, iframe, noscript').remove();

    // Título
    const title =
      $('meta[property="og:title"]').attr('content') ||
      $('h1').first().text().trim() ||
      $('title').text().trim() ||
      '';

    // Descrição
    const description =
      $('meta[property="og:description"]').attr('content') ||
      $('meta[name="description"]').attr('content') ||
      '';

    // Imagem principal
    const image =
      $('meta[property="og:image"]').attr('content') ||
      $('meta[name="twitter:image"]').attr('content') ||
      $('article img').first().attr('src') ||
      $('img').first().attr('src') ||
      '';

    // Autor
    const author =
      $('meta[name="author"]').attr('content') ||
      $('[rel="author"]').first().text().trim() ||
      $('[class*="author"]').first().text().trim() ||
      $('[itemprop="author"]').first().text().trim() ||
      '';

    // Data de publicação
    const publishedAt =
      $('meta[property="article:published_time"]').attr('content') ||
      $('time[datetime]').first().attr('datetime') ||
      $('[itemprop="datePublished"]').attr('content') ||
      '';

    // Corpo do artigo
    let body = '';
    const articleSelectors = [
      'article',
      '[class*="article-body"]',
      '[class*="article-content"]',
      '[class*="post-content"]',
      '[class*="entry-content"]',
      '[class*="story-body"]',
      '[class*="news-body"]',
      'main',
      '.content'
    ];

    for (const selector of articleSelectors) {
      const el = $(selector);
      if (el.length && el.text().trim().length > 200) {
        body = el
          .find('p')
          .map((_, p) => $(p).text().trim())
          .get()
          .filter(t => t.length > 30)
          .join('\n\n');
        break;
      }
    }

    // Fallback: todos os parágrafos da página
    if (!body) {
      body = $('p')
        .map((_, p) => $(p).text().trim())
        .get()
        .filter(t => t.length > 50)
        .join('\n\n');
    }

    // Fonte/domínio
    const source = new URL(url).hostname.replace(/^www\./, '');

    return {
      url,
      title: title.trim(),
      description: description.trim(),
      image: image.trim(),
      author: author.trim(),
      publishedAt: publishedAt.trim(),
      source,
      body: body.trim(),
      bodyLength: body.trim().length,
      scrapedAt: new Date().toISOString()
    };

  } catch (err) {
    throw new Error(`Erro ao extrair artigo: ${err.message}`);
  }
}

module.exports = { extractArticle };
