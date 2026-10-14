'use strict';

/**
 * Categorias reais da GDELT DOC 2.0 API, por código GKG tema (theme:CODE).
 * https://api.gdeltproject.org/api/v2/doc/doc  (documentação de temas: GDELT GKG Codebook)
 * Cada entrada: id interno -> { label, query } onde query é a cláusula OR de temas.
 */
const CATEGORIES = {
  geral: { label: 'Geral', query: '' },
  economia: {
    label: 'Economia',
    query:
      'theme:ECON_ECONOMY OR theme:ECON_STOCKMARKET OR theme:ECON_INFLATION OR ' +
      'theme:ECON_BANKRUPTCY OR theme:ECON_SUBSIDIES OR theme:ECON_TRADE_DISPUTE OR ' +
      'theme:ECON_CENTRALBANK OR theme:TAX_FNCACT_ECONOMIST',
  },
  politica: {
    label: 'Política',
    query:
      'theme:TAX_FNCACT_PRESIDENT OR theme:TAX_FNCACT_MINISTER OR theme:GOV_LEGISLATION OR ' +
      'theme:ELECTION OR theme:GENERAL_GOVERNMENT OR theme:LEADER',
  },
  desporto: { label: 'Desporto', query: 'theme:SOCCER OR theme:SPORTS' },
  saude: {
    label: 'Saúde',
    query: 'theme:HEALTH_PANDEMIC OR theme:MEDICAL OR theme:EPU_CATS_HEALTHCARE OR theme:WHO',
  },
  ambiente: {
    label: 'Ambiente',
    query:
      'theme:ENV_CLIMATECHANGE OR theme:NATURAL_DISASTER OR theme:ENV_DISASTER OR theme:DROUGHT',
  },
  tecnologia: { label: 'Tecnologia', query: 'theme:TECHNOLOGY OR theme:CYBER_ATTACK OR theme:SCIENCE' },
  energia: { label: 'Energia', query: 'theme:ENERGY OR theme:OIL_AND_GAS OR theme:ALTERNATIVE_ENERGY' },
  seguranca: {
    label: 'Segurança',
    query: 'theme:ARMEDCONFLICT OR theme:TERROR OR theme:MILITARY OR theme:KILL',
  },
  justica: {
    label: 'Justiça',
    query: 'theme:CRIME_ILLEGAL_DRUGS OR theme:CORRUPTION OR theme:TRIAL OR theme:LEGISLATION',
  },
  educacao: { label: 'Educação', query: 'theme:EDUCATION' },
  protestos: { label: 'Protestos', query: 'theme:PROTEST OR theme:UNREST_GENERAL' },
  direitoshum: { label: 'Direitos Humanos', query: 'theme:HUMAN_RIGHTS OR theme:REFUGEES' },
  negocios: {
    label: 'Negócios',
    query: 'theme:BUSINESS OR theme:MANMADE_DISASTER_IMPLIED OR theme:TAX_FNCACT_EXECUTIVE',
  },
};

const REGIONS = [
  { code: 'AO', label: 'Angola', country: 'AO', lang: 'por' },
  { code: 'PT', label: 'Portugal', country: 'PO', lang: 'por' },
  { code: 'BR', label: 'Brasil', country: 'BR', lang: 'por' },
  { code: 'AF', label: 'África', country: '', lang: '' },
  { code: 'INT', label: 'Mundo', country: '', lang: '' },
];

module.exports = { CATEGORIES, REGIONS };