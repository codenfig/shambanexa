const MEDICINE = 'assets/catalog/veterinary-medicines.png';
const FEED = 'assets/catalog/animal-feeds.png';
const SUPPLY = 'assets/catalog/agricultural-supplies.png';

const SPECIES: Record<string, string> = {
  cattle: 'assets/catalog/cattle.png',
  goats: 'assets/catalog/goats.png',
  goat: 'assets/catalog/goats.png',
  sheep: 'assets/catalog/sheep.png',
  poultry: 'assets/catalog/poultry.png',
  pigs: 'assets/catalog/pigs.png',
  pig: 'assets/catalog/pigs.png',
  fish: 'assets/catalog/fish.png',
  rabbits: 'assets/catalog/rabbits.png',
  rabbit: 'assets/catalog/rabbits.png',
  bees: 'assets/catalog/bees.png',
  bee: 'assets/catalog/bees.png'
};

export const categoryPictures = {
  MEDICINE,
  FEED,
  SUPPLY
};

export function stockPicture(category: string, productType: string | null): string | null {
  const type = (productType || '').toLowerCase();
  if (type.includes('antibiotic')) {
    return 'assets/catalog/antibiotics.png';
  }
  if (type.includes('vaccin')) {
    return 'assets/catalog/vaccines.png';
  }
  if (type.includes('deworm')) {
    return 'assets/catalog/dewormers.png';
  }
  if (type.includes('vitamin')) {
    return 'assets/catalog/vitamins.png';
  }
  if (category === 'MEDICINE') {
    return MEDICINE;
  }
  if (category === 'FEED') {
    return FEED;
  }
  if (category === 'SUPPLY') {
    return SUPPLY;
  }
  return null;
}

export function speciesPicture(name: string | null | undefined): string | null {
  if (!name) {
    return null;
  }
  return SPECIES[name.trim().toLowerCase()] || null;
}
