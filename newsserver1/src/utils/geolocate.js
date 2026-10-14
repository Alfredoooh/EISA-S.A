const axios = require('axios');

// Mapa de países para códigos de idioma e fontes RSS
const countryMap = {
  'AO': { name: 'Angola', lang: 'pt', gdeltCountry: 'Angola' },
  'PT': { name: 'Portugal', lang: 'pt', gdeltCountry: 'Portugal' },
  'BR': { name: 'Brasil', lang: 'pt', gdeltCountry: 'Brazil' },
  'MZ': { name: 'Moçambique', lang: 'pt', gdeltCountry: 'Mozambique' },
  'CV': { name: 'Cabo Verde', lang: 'pt', gdeltCountry: 'Cape Verde' },
  'US': { name: 'Estados Unidos', lang: 'en', gdeltCountry: 'United States' },
  'GB': { name: 'Reino Unido', lang: 'en', gdeltCountry: 'United Kingdom' },
  'FR': { name: 'França', lang: 'fr', gdeltCountry: 'France' },
  'DE': { name: 'Alemanha', lang: 'de', gdeltCountry: 'Germany' },
  'ZA': { name: 'África do Sul', lang: 'en', gdeltCountry: 'South Africa' },
  'NG': { name: 'Nigéria', lang: 'en', gdeltCountry: 'Nigeria' },
  'KE': { name: 'Quénia', lang: 'en', gdeltCountry: 'Kenya' },
};

async function getCountryFromCoords(lat, lon) {
  try {
    const response = await axios.get(
      `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lon}&format=json`,
      {
        headers: { 'User-Agent': 'NewsAPI/1.0' },
        timeout: 5000
      }
    );
    const code = response.data?.address?.country_code?.toUpperCase();
    return {
      code,
      info: countryMap[code] || { name: code, lang: 'en', gdeltCountry: code }
    };
  } catch (err) {
    throw new Error(`Geolocalização falhou: ${err.message}`);
  }
}

module.exports = { getCountryFromCoords, countryMap };
