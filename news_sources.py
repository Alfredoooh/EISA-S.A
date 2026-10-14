"""Catálogo de fontes de notícias.

Campos de cada fonte:
  id          identificador único (slug)
  name        nome apresentado
  region      AO | PT | BR | AF | INT
  category    Geral | Economia | Desporto | Oficial | Lifestyle
  url         página inicial (serve para descobrir o RSS ou, em último caso, ler os títulos do HTML)
  feeds       feeds RSS/Atom conhecidos (opcional; se falharem, o servidor descobre sozinho)
  lang        idioma principal: pt | en | fr
  gnews_only  True = usar só o Google News (sites sem RSS que bloqueiam robôs)
  enabled     False para desativar uma fonte sem a apagar
"""

REGIONS = [
    {"code": "AO", "label": "Angola"},
    {"code": "PT", "label": "Portugal"},
    {"code": "BR", "label": "Brasil"},
    {"code": "AF", "label": "África"},
    {"code": "INT", "label": "Mundo"},
]


def _src(sid, name, region, category, url, feeds=(), lang="pt", gnews_only=False, enabled=True):
    return {
        "id": sid,
        "name": name,
        "region": region,
        "category": category,
        "url": url,
        "feeds": list(feeds),
        "lang": lang,
        "gnews_only": gnews_only,
        "enabled": enabled,
    }


SOURCES = [
    # ── Angola ──────────────────────────────────────────────
    _src("jornal-de-angola", "Jornal de Angola", "AO", "Geral", "https://jornaldeangola.ao/"),
    _src("o-pais", "O País", "AO", "Geral", "https://opais.ao/"),
    _src("angop", "ANGOP", "AO", "Geral", "https://www.angop.ao/"),
    _src("novo-jornal", "Novo Jornal", "AO", "Geral", "https://novojornal.co.ao/"),
    _src("expansao", "Expansão", "AO", "Economia", "https://www.expansao.co.ao/"),
    _src("valor-economico", "Valor Económico", "AO", "Economia", "https://valoreconomico.co.ao/",
         ["https://valoreconomico.co.ao/feed/"]),
    _src("club-k", "Club-K", "AO", "Geral", "https://club-k.net/"),
    _src("maka-angola", "Maka Angola", "AO", "Geral", "https://www.makaangola.org/",
         ["https://www.makaangola.org/feed/"]),
    _src("folha-8", "Folha 8", "AO", "Geral", "https://jornalf8.net/", ["https://jornalf8.net/feed/"]),
    _src("correio-da-kianda", "Correio da Kianda", "AO", "Geral", "https://correiokianda.info/",
         ["https://correiokianda.info/feed/"]),
    _src("angorussia", "AngoRussia", "AO", "Geral", "https://angorussia.com/", ["https://angorussia.com/feed/"]),
    _src("angola24horas", "Angola24Horas", "AO", "Geral", "https://angola24horas.com/",
         ["https://angola24horas.com/feed/"]),
    _src("times-de-todos", "Times de Todos", "AO", "Geral", "https://timesdetodos.com/",
         ["https://timesdetodos.com/feed/"]),
    _src("ver-angola", "Ver Angola", "AO", "Geral", "https://www.verangola.net/"),
    _src("platina-line", "Platina Line", "AO", "Geral", "https://platinaline.com/", ["https://platinaline.com/feed/"]),
    _src("portal-de-angola", "Portal de Angola", "AO", "Geral", "https://www.portaldeangola.com/"),
    _src("angonoticias", "AngoNotícias", "AO", "Geral", "https://www.angonoticias.com/"),
    _src("o-apostolado", "O Apostolado", "AO", "Geral", "https://jornalapostoladoangola.org/",
         ["https://jornalapostoladoangola.org/feed/"]),
    _src("rna", "RNA — Rádio Nacional de Angola", "AO", "Geral", "https://www.rna.ao/", ["https://www.rna.ao/feed/"]),
    _src("tpa", "TPA — Televisão Pública de Angola", "AO", "Geral", "https://tpa.ao/"),
    _src("governo-de-angola", "Governo de Angola", "AO", "Oficial", "https://governo.gov.ao/"),
    # URL oficial por confirmar: preencha o url e mude enabled para True
    _src("angola-headlines", "Angola Headlines", "AO", "Geral", "", enabled=False),

    # ── Portugal ────────────────────────────────────────────
    _src("rtp-noticias", "RTP Notícias", "PT", "Geral", "https://www.rtp.pt/noticias/",
         ["https://www.rtp.pt/noticias/rss"]),
    _src("observador", "Observador", "PT", "Geral", "https://observador.pt/", ["https://observador.pt/feed/"]),
    _src("publico", "Público", "PT", "Geral", "https://www.publico.pt/",
         ["https://www.publico.pt/rss", "https://feeds.feedburner.com/PublicoRSS"]),
    _src("eco", "ECO", "PT", "Economia", "https://eco.sapo.pt/", ["https://eco.sapo.pt/feed/"]),
    _src("sic-noticias", "SIC Notícias", "PT", "Geral", "https://sicnoticias.pt/"),
    _src("cnn-portugal", "CNN Portugal", "PT", "Geral", "https://cnnportugal.iol.pt/"),
    _src("tsf", "TSF", "PT", "Geral", "https://www.tsf.pt/"),
    _src("jn", "Jornal de Notícias", "PT", "Geral", "https://www.jn.pt/"),
    _src("correio-da-manha", "Correio da Manhã", "PT", "Geral", "https://www.cmjornal.pt/"),
    _src("sapo-noticias", "SAPO Notícias", "PT", "Geral", "https://www.sapo.pt/noticias/ultimas"),
    _src("noticias-ao-minuto", "Notícias ao Minuto", "PT", "Geral", "https://www.noticiasaominuto.com/",
         ["https://www.noticiasaominuto.com/rss/ultimas"]),
    _src("a-bola", "A BOLA", "PT", "Desporto", "https://www.abola.pt/"),
    _src("zap-aeiou", "ZAP.aeiou", "PT", "Geral", "https://zap.aeiou.pt/", ["https://zap.aeiou.pt/feed/"]),
    _src("magg", "MAGG (SAPO)", "PT", "Lifestyle", "https://magg.sapo.pt/"),

    # ── Brasil ──────────────────────────────────────────────
    _src("g1", "G1", "BR", "Geral", "https://g1.globo.com/", ["https://g1.globo.com/rss/g1/"]),
    _src("folha-de-sp", "Folha de S.Paulo", "BR", "Geral", "https://www.folha.uol.com.br/",
         ["https://feeds.folha.uol.com.br/emcimadahora/rss091.xml"]),
    _src("uol-noticias", "UOL Notícias", "BR", "Geral", "https://noticias.uol.com.br/",
         ["https://rss.uol.com.br/feed/noticias.xml"]),
    _src("estadao", "Estadão", "BR", "Geral", "https://www.estadao.com.br/"),
    _src("cnn-brasil", "CNN Brasil", "BR", "Geral", "https://www.cnnbrasil.com.br/",
         ["https://www.cnnbrasil.com.br/feed/"]),
    _src("poder360", "Poder360", "BR", "Geral", "https://www.poder360.com.br/",
         ["https://www.poder360.com.br/feed/"]),
    _src("agencia-brasil", "Agência Brasil", "BR", "Geral", "https://agenciabrasil.ebc.com.br/",
         ["https://agenciabrasil.ebc.com.br/rss/ultimasnoticias/feed.xml"]),
    _src("jovem-pan", "Jovem Pan", "BR", "Geral", "https://jovempan.com.br/ultimas-noticias/",
         ["https://jovempan.com.br/feed"]),

    # ── África ──────────────────────────────────────────────
    _src("dw-africa", "DW África", "AF", "Geral", "https://www.dw.com/pt-002/"),
    _src("africanews", "Africanews", "AF", "Geral", "https://www.africanews.com/",
         ["https://www.africanews.com/feed/rss"], lang="en"),
    _src("allafrica", "AllAfrica", "AF", "Geral", "https://allafrica.com/",
         ["https://allafrica.com/tools/headlines/rdf/latest/headlines.rdf"], lang="en"),

    # ── Mundo ───────────────────────────────────────────────
    _src("reuters", "Reuters", "INT", "Geral", "https://www.reuters.com/", lang="en", gnews_only=True),
    _src("ap-news", "Associated Press", "INT", "Geral", "https://apnews.com/", lang="en", gnews_only=True),
    _src("bbc-news", "BBC News", "INT", "Geral", "https://www.bbc.com/news",
         ["https://feeds.bbci.co.uk/news/rss.xml"], lang="en"),
    _src("al-jazeera", "Al Jazeera", "INT", "Geral", "https://www.aljazeera.com/",
         ["https://www.aljazeera.com/xml/rss/all.xml"], lang="en"),
    _src("france-24", "France 24", "INT", "Geral", "https://www.france24.com/",
         ["https://www.france24.com/en/rss"], lang="en"),
]

_ORDER = ["Geral", "Economia", "Desporto", "Oficial", "Lifestyle"]
_used = {s["category"] for s in SOURCES}
CATEGORIES = [c for c in _ORDER if c in _used] + sorted(_used - set(_ORDER))
