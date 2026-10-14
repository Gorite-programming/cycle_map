const fs = require('fs');
const path = require('path');
const basemaps = require('@protomaps/basemaps');

// Protomaps dark flavor uses near-background greys (#3d3d3d–#7a7a7a on #1f1f1f).
// Remap paint only; light flavor is left unchanged.
const DARK_TEXT = {
  '#3d3d3d': '#D6D6D6',
  '#525252': '#E8E8E8',
  '#5c5c5c': '#EEEEEE',
  '#666666': '#F2F2F2',
  '#717784': '#C5D0E0',
  '#7a7a7a': '#F5F5F5',
  '#1f1f1f': '#E8E8E8',
};
const DARK_HALO = {
  '#1f1f1f': '#0A0A0A',
  '#212121': '#000000',
  '#31353f': '#0B1018',
};

function remapColor(value, table) {
  if (typeof value === 'string') {
    const mapped = table[value] || table[value.toLowerCase()];
    return mapped || value;
  }
  if (Array.isArray(value)) {
    return value.map((item) => remapColor(item, table));
  }
  return value;
}

function boostDarkLabelContrast(layer) {
  if (layer.type !== 'symbol') {
    return;
  }
  const paint = layer.paint || (layer.paint = {});
  if (paint['text-color'] != null) {
    paint['text-color'] = remapColor(paint['text-color'], DARK_TEXT);
    paint['text-halo-color'] = paint['text-halo-color'] != null
      ? remapColor(paint['text-halo-color'], DARK_HALO)
      : '#0A0A0A';
    const width = paint['text-halo-width'];
    paint['text-halo-width'] = Math.max(typeof width === 'number' ? width : 0, 1.5);
  }
}

const repoRoot = path.resolve(__dirname, '../../..');
const styleDir = path.join(repoRoot, 're_test/app/src/main/assets/styles');

for (const theme of ['light', 'dark']) {
  const stylePath = path.join(styleDir, `protomaps_${theme}.json`);
  const style = JSON.parse(fs.readFileSync(stylePath, 'utf8'));
  const labelLayers = basemaps.layers('protomaps', basemaps.namedFlavor(theme), {
    lang: 'ja',
    labelsOnly: true,
  });
  for (const layer of labelLayers) {
    if (layer.layout?.['text-font']) {
      // Ship one compact fontstack; MapLibre Native renders CJK Unified
      // Ideographs locally and the bundled ranges cover Japanese kana.
      layer.layout['text-font'] = ['Noto Sans Regular'];
    }
    if (theme === 'dark') {
      boostDarkLabelContrast(layer);
    }
  }
  const labelIds = new Set(labelLayers.map((layer) => layer.id));
  style.layers = style.layers.filter((layer) => !labelIds.has(layer.id));
  const existingIds = new Set(style.layers.map((layer) => layer.id));
  const duplicates = labelLayers.filter((layer) => existingIds.has(layer.id));
  if (duplicates.length) {
    throw new Error(`Duplicate ${theme} style layer IDs: ${duplicates.map((x) => x.id).join(', ')}`);
  }
  style.layers.push(...labelLayers);
  fs.writeFileSync(stylePath, `${JSON.stringify(style, null, 2)}\n`);
  console.log(`${theme}: appended ${labelLayers.length} Japanese-label/symbol layers`);
}
