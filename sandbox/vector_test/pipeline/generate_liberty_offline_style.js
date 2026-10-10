const fs = require('fs');
const bm = require('@protomaps/basemaps');

// 本家 Liberty に合わせたカラーパレット
const libertyPalette = {
  ...bm.LIGHT,
  background: '#f8f4f0',
  earth: '#f8f4f0',
  park_a: '#d8e8c8',
  park_b: '#d8e8c8',
  hospital: '#f1ebe4',
  industrial: '#eceae6',
  school: '#f1ede6',
  wood_a: '#d8e8c8',
  wood_b: '#a0d9a0',
  pedestrian: '#f3f0ec',
  scrub_a: '#e2ecd8',
  scrub_b: '#bce0bf',
  glacier: '#e7e7e7',
  sand: '#f5f0e6',
  beach: '#f5f0e6',
  aerodrome: '#dadbdf',
  runway: '#e9e9ed',
  water: '#9ebdff', // Liberty 水域: rgb(158,189,255)
  zoo: '#c6dcdc',
  military: '#dcdcdc',

  // トンネル
  tunnel_other_casing: '#cfcdca',
  tunnel_minor_casing: '#cfcdca',
  tunnel_link_casing: '#e9ac77',
  tunnel_major_casing: '#e9ac77',
  tunnel_highway_casing: '#e9ac77',
  tunnel_other: '#f5f3f0',
  tunnel_minor: '#ffffff',
  tunnel_link: '#ffeaa0',
  tunnel_major: '#ffeaa0',
  tunnel_highway: '#fed174',

  // 道路ケーシング（フチ取り）
  minor_service_casing: '#cfcdca',
  minor_casing: '#cfcdca',         // 一般道フチ: #cfcdca
  link_casing: '#e9ac77',          // 接続路フチ: #e9ac77
  major_casing_late: '#e9ac77',    // 主要道フチ: #e9ac77
  highway_casing_late: '#e9ac77',  // 高速道フチ: #e9ac77
  major_casing_early: '#e9ac77',
  highway_casing_early: '#e9ac77',

  // 道路本体（中塗り）
  other: '#f5f3f0',
  minor_service: '#ffffff',
  minor_a: '#ffffff',
  minor_b: '#ffffff',              // 一般道中塗り: 純白 #ffffff
  link: '#ffeaa0',                 // 接続路中塗り: パステルイエロー #ffeaa0
  major: '#ffeaa0',                // 主要道中塗り: パステルイエロー #ffeaa0
  highway: '#fed174',              // 高速道中塗り: オレンジイエロー #fed174
  railway: '#bbbbbb',              // 鉄道: #bbbbbb
  boundaries: '#adadad',

  // 橋
  bridges_other_casing: '#cfcdca',
  bridges_minor_casing: '#cfcdca',
  bridges_link_casing: '#e9ac77',
  bridges_major_casing: '#e9ac77',
  bridges_highway_casing: '#e9ac77',
  bridges_other: '#f5f3f0',
  bridges_minor: '#ffffff',
  bridges_link: '#ffeaa0',
  bridges_major: '#ffeaa0',
  bridges_highway: '#fed174',

  // 建物
  buildings: '#dedad5',

  // 陸地被覆
  landcover: {
    grassland: '#d8e8c8',
    barren: '#f5f0e6',
    urban_area: '#f0ece8',
    farmland: '#d8e8c8',
    glacier: '#ffffff',
    scrub: '#e2ecd8',
    forest: '#d8e8c8'
  }
};

const rawLayers = bm.layers('protomaps', libertyPalette);

// buildings レイヤーを 2D / 3D 切り替え可能な 2 つのレイヤーに置換
const processedLayers = [];
for (const layer of rawLayers) {
  if (layer.id === 'buildings') {
    processedLayers.push({
      id: 'buildings-2d',
      type: 'fill',
      source: 'protomaps',
      'source-layer': 'buildings',
      minzoom: 13,
      paint: {
        'fill-color': '#dedad5',
        'fill-outline-color': '#cfcdca',
        'fill-opacity': 0.95
      }
    });
    processedLayers.push({
      id: 'buildings-3d',
      type: 'fill-extrusion',
      source: 'protomaps',
      'source-layer': 'buildings',
      minzoom: 13,
      paint: {
        'fill-extrusion-color': '#dedad5',
        'fill-extrusion-height': 16,
        'fill-extrusion-base': 0,
        'fill-extrusion-opacity': 0.5
      },
      layout: {
        visibility: 'none'
      }
    });
  } else {
    processedLayers.push(layer);
  }
}

const style = {
  version: 8,
  glyphs: 'https://protomaps.github.io/basemaps-assets/fonts/{fontstack}/{range}.pbf',
  sprite: 'https://protomaps.github.io/basemaps-assets/sprites/v4/light',
  sources: {
    protomaps: {
      type: 'vector',
      url: '__LOCAL_PMTILES__'
    }
  },
  layers: processedLayers
};

const outputPath = '../app/src/main/assets/styles/protomaps_light.json';
fs.writeFileSync(outputPath, JSON.stringify(style, null, 2), 'utf-8');
console.log(`Generated Liberty-style offline vector style successfully -> ${outputPath}`);
