'use strict';

/**
 * Categorias internas do agregador. Cada fonte mapeia para o seu próprio
 * sistema de categorias nativo quando existe equivalente direto; quando não
 * existe, a fonte simplesmente não participa nessa categoria.
 * Sem filtro de país/idioma: cada API devolve o que tiver, sem restrição —
 * o app cliente trata da tradução.
 */
const CATEGORIES = {
  geral: {
    label: 'Geral',
    gdelt: '',
    guardian: '',
    nyt: 'home',
    gnews: 'general',
    newsdata: '',
  },
  economia: {
    label: 'Economia',
    gdelt: 'theme:ECON_ECONOMY OR theme:ECON_STOCKMARKET OR theme:ECON_INFLATION OR theme:ECON_TRADE_DISPUTE OR theme:ECON_CENTRALBANK',
    guardian: 'business',
    nyt: 'business',
    gnews: 'business',
    newsdata: 'business',
  },
  politica: {
    label: 'Política',
    gdelt: 'theme:GOV_LEGISLATION OR theme:ELECTION OR theme:GENERAL_GOVERNMENT OR theme:LEADER',
    guardian: 'politics',
    nyt: 'politics',
    gnews: 'nation',
    newsdata: 'politics',
  },
  desporto: {
    label: 'Desporto',
    gdelt: 'theme:SOCCER OR theme:SPORTS',
    guardian: 'sport',
    nyt: 'sports',
    gnews: 'sports',
    newsdata: 'sports',
  },
  saude: {
    label: 'Saúde',
    gdelt: 'theme:HEALTH_PANDEMIC OR theme:MEDICAL OR theme:WHO',
    guardian: 'society',
    nyt: 'health',
    gnews: 'health',
    newsdata: 'health',
  },
  ambiente: {
    label: 'Ambiente',
    gdelt: 'theme:ENV_CLIMATECHANGE OR theme:NATURAL_DISASTER OR theme:ENV_DISASTER',
    guardian: 'environment',
    nyt: 'climate',
    gnews: 'science',
    newsdata: 'environment',
  },
  tecnologia: {
    label: 'Tecnologia',
    gdelt: 'theme:TECHNOLOGY OR theme:CYBER_ATTACK OR theme:SCIENCE',
    guardian: 'technology',
    nyt: 'technology',
    gnews: 'technology',
    newsdata: 'technology',
  },
  energia: {
    label: 'Energia',
    gdelt: 'theme:ENERGY OR theme:OIL_AND_GAS OR theme:ALTERNATIVE_ENERGY',
    guardian: 'environment/energy',
    nyt: '',
    gnews: 'business',
    newsdata: 'business',
  },
  seguranca: {
    label: 'Segurança',
    gdelt: 'theme:ARMEDCONFLICT OR theme:TERROR OR theme:MILITARY',
    guardian: 'world',
    nyt: 'world',
    gnews: 'world',
    newsdata: 'world',
  },
  justica: {
    label: 'Justiça',
    gdelt: 'theme:CRIME_ILLEGAL_DRUGS OR theme:CORRUPTION OR theme:TRIAL',
    guardian: 'law',
    nyt: '',
    gnews: 'nation',
    newsdata: 'crime',
  },
  educacao: {
    label: 'Educação',
    gdelt: 'theme:EDUCATION',
    guardian: 'education',
    nyt: '',
    gnews: '',
    newsdata: '',
  },
  protestos: {
    label: 'Protestos',
    gdelt: 'theme:PROTEST OR theme:UNREST_GENERAL',
    guardian: 'world',
    nyt: '',
    gnews: '',
    newsdata: '',
  },
  direitoshum: {
    label: 'Direitos Humanos',
    gdelt: 'theme:HUMAN_RIGHTS OR theme:REFUGEES',
    guardian: 'world/human-rights',
    nyt: '',
    gnews: '',
    newsdata: '',
  },
  negocios: {
    label: 'Negócios',
    gdelt: 'theme:BUSINESS OR theme:TAX_FNCACT_EXECUTIVE',
    guardian: 'business',
    nyt: 'business',
    gnews: 'business',
    newsdata: 'business',
  },
  entretenimento: {
    label: 'Entretenimento',
    gdelt: 'theme:ENTERTAINMENT OR theme:ARTS',
    guardian: 'culture',
    nyt: 'arts',
    gnews: 'entertainment',
    newsdata: 'entertainment',
  },
};

module.exports = { CATEGORIES };