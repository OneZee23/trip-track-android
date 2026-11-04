#!/usr/bin/env python3
"""TripTrack's map styles, light and dark, drawn after the iPhone's Apple Maps.

iOS draws every map with MapKit's standard map (flat elevation, no points of interest): pale green land with
greener forest in the light theme, teal land with darker forest, deep blue water and purple borders in the dark one.
Android draws OpenFreeMap tiles; the stock Liberty and Dark styles looked like a different app (owner, 4 Nov 2026:
«Это две разные вещи, координально, а мне надо чтобы было как на IOS»).

Input:  tools/mapstyle/liberty.json, OpenFreeMap Liberty (layer structure, sources, fonts, sprite), as of 4 Nov 2026.
Output: core/map/src/main/assets/maps/triptrack-light.json and triptrack-dark.json.
The colours were sampled from MKMapSnapshotter renders of real feed routes (docs/reports/w16-maps.md).

Data © OpenMapTiles © OpenStreetMap contributors; the attribution travels with the vector source.
"""
import copy
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'tools/mapstyle/liberty.json'
OUT = ROOT / 'core/map/src/main/assets/maps'

PALETTES = {
    'light': {
        'land': '#eaefd0', 'land_far': '#d5e7b8', 'forest': '#c0e0a3', 'grass': '#d4e8b6', 'park': '#cde5b1', 'urban': '#f7f5ee',
        'commercial': '#f3ece9', 'hospital': '#f6e3e3', 'school': '#f4efdc', 'cemetery': '#d6e7c4',
        'ice': '#f6fafc', 'sand': '#f1ebcf', 'wetland': '#cfe6c6',
        'water': '#a2daf5', 'sea': '#5eb5f3', 'river': '#78b7ec',
        'border_country': '#a26ab1', 'border_state': '#c5a9c9',
        'motorway': '#a3a3a6', 'motorway_casing': '#909094', 'trunk': '#bcbcbf', 'trunk_casing': '#a7a7aa',
        'major': '#ffffff', 'major_casing': '#cfcfcf', 'minor': '#ffffff', 'minor_casing': '#dedede',
        'path': '#d8d3c4', 'rail': '#c9c9c9', 'aeroway': '#e3e3e6', 'building': '#e9e6df', 'building_line': '#dcd8cf',
        'label': '#262626', 'label_soft': '#6e6e6e', 'halo': '#ffffff', 'water_label': '#3b7cc4',
    },
    'dark': {
        'land': '#4d7266', 'land_far': '#3f6d69', 'forest': '#336a6b', 'grass': '#456f68', 'park': '#3f6758', 'urban': '#3a485b',
        'commercial': '#4a4567', 'hospital': '#5a4458', 'school': '#4d4a55', 'cemetery': '#3b6359',
        'ice': '#6f8a95', 'sand': '#5f6c62', 'wetland': '#3d6a66',
        'water': '#243882', 'sea': '#101d4c', 'river': '#6f84bd',
        'border_country': '#7a3f8f', 'border_state': '#7c6c8c',
        'motorway': '#9aa5c6', 'motorway_casing': '#7d88a8', 'trunk': '#8590ae', 'trunk_casing': '#6c7693',
        'major': '#6c7489', 'major_casing': '#56607a', 'minor': '#596277', 'minor_casing': '#4a5468',
        'path': '#53606a', 'rail': '#5d6676', 'aeroway': '#4a5568', 'building': '#46536a', 'building_line': '#3f4b60',
        'label': '#f2f4f7', 'label_soft': '#c4cad6', 'halo': '#1f2a33', 'water_label': '#9fb3ec',
    },
}

# Points of interest are off on iOS (`pointOfInterestFilter = .excludingAll`); hill shading, 3D buildings, one-way
# arrows and outlines are not in Apple's flat standard map.
DROP = {
    'natural_earth', 'park_outline', 'building-3d', 'road_area_pattern',
    'poi_r20', 'poi_r7', 'poi_r1', 'poi_transit', 'airport',
    'road_one_way_arrow', 'road_one_way_arrow_opposite',
    'tunnel_major_rail_hatching', 'tunnel_transit_rail_hatching', 'road_major_rail_hatching',
    'road_transit_rail_hatching', 'bridge_major_rail_hatching', 'bridge_transit_rail_hatching',
}

# One name per label; MapLabels rewrites it to the app's language when the style loads.
NAME = ['coalesce', ['get', 'name:en'], ['get', 'name:latin'], ['get', 'name']]


def road_colour(layer_id, p):
    """Fill and casing colours of a transportation line by its class in the id."""
    casing = layer_id.endswith('_casing')
    if 'motorway' in layer_id:
        return p['motorway_casing'] if casing else p['motorway']
    if 'trunk_primary' in layer_id:
        return p['trunk_casing'] if casing else p['trunk']
    if 'secondary_tertiary' in layer_id or 'link' in layer_id:
        return p['major_casing'] if casing else p['major']
    if 'path_pedestrian' in layer_id or 'service_track' in layer_id:
        return p['minor_casing'] if casing else p['path']
    if 'rail' in layer_id:
        return p['rail']
    return p['minor_casing'] if casing else p['minor']


def restyle(layer, p):
    lid, kind = layer['id'], layer['type']
    paint = layer.setdefault('paint', {})
    layout = layer.setdefault('layout', {})
    if lid == 'background':
        # From afar Apple's land is mostly forest: its global land cover has patches OpenMapTiles lacks below
        # zoom 8, so the far land takes the mean of forest and open land, and the near land the open colour.
        paint['background-color'] = ['interpolate', ['linear'], ['zoom'], 5, p['land_far'], 9, p['land']]
    elif lid == 'park':
        layer['paint'] = {'fill-color': p['park'], 'fill-opacity': 1}
    elif lid == 'landuse_residential':
        layer['paint'] = {'fill-color': p['urban'], 'fill-opacity': ['interpolate', ['linear'], ['zoom'], 6, 0.7, 10, 1]}
        layer.pop('maxzoom', None)
    elif lid == 'landcover_wood':
        layer['paint'] = {'fill-color': p['forest'], 'fill-antialias': False}
    elif lid == 'landcover_grass':
        # Lawns come in before the city's own blocks would let them: Apple shows them only close up.
        layer['paint'] = {'fill-color': p['grass'], 'fill-antialias': False}
        layer['minzoom'] = 12.5
    elif lid == 'landcover_wetland':
        layer['paint'] = {'fill-color': p['wetland'], 'fill-antialias': False}
    elif lid == 'landcover_ice':
        layer['paint'] = {'fill-color': p['ice'], 'fill-antialias': False}
    elif lid == 'landcover_sand':
        layer['paint'] = {'fill-color': p['sand'], 'fill-antialias': False}
    elif lid in ('landuse_pitch', 'landuse_track'):
        layer['paint'] = {'fill-color': p['park']}
    elif lid == 'landuse_cemetery':
        layer['paint'] = {'fill-color': p['cemetery']}
    elif lid == 'landuse_hospital':
        layer['paint'] = {'fill-color': p['hospital']}
    elif lid == 'landuse_school':
        layer['paint'] = {'fill-color': p['school']}
    elif lid == 'water':
        # Apple's sea is deeper than its lakes in both themes.
        layer['paint'] = {'fill-color': ['match', ['get', 'class'], 'ocean', p['sea'], p['water']], 'fill-antialias': True}
    elif lid.startswith('waterway') and kind == 'line':
        paint['line-color'] = p['river']
        paint.pop('line-dasharray', None) if lid != 'waterway_tunnel' else None
        paint['line-width'] = ['interpolate', ['exponential', 1.3], ['zoom'], 3, 0.9, 8, 1.2, 12, 1.8, 16, 4, 20, 8]
    elif lid.startswith('aeroway') and kind in ('fill', 'line'):
        if kind == 'fill':
            layer['paint'] = {'fill-color': p['aeroway'], 'fill-opacity': 1}
        else:
            paint['line-color'] = p['aeroway']
    elif lid == 'building':
        layer['paint'] = {'fill-color': p['building'], 'fill-outline-color': p['building_line']}
        layer['minzoom'] = 15
        layer.pop('maxzoom', None)
    elif lid == 'boundary_2':
        layer['paint'] = {
            'line-color': p['border_country'],
            'line-width': ['interpolate', ['linear'], ['zoom'], 3, 0.8, 6, 1.4, 12, 2.5],
        }
    elif lid == 'boundary_3':
        layer['paint'] = {
            'line-color': p['border_state'],
            'line-dasharray': [2, 2],
            'line-width': ['interpolate', ['linear'], ['zoom'], 5, 0.6, 10, 1.2, 14, 1.6],
        }
    elif lid == 'boundary_disputed':
        paint['line-color'] = p['border_country']
    elif kind == 'line' and layer.get('source-layer') == 'transportation':
        paint['line-color'] = road_colour(lid, p)
        paint.pop('line-opacity', None) if 'tunnel' not in lid else None
    elif kind == 'symbol':
        style_label(layer, p)


def style_label(layer, p):
    lid = layer['id']
    paint, layout = layer['paint'], layer['layout']
    if 'text-field' in layout and not lid.startswith(('highway-shield', 'road_shield')):
        layout['text-field'] = NAME
    paint['text-halo-color'] = p['halo']
    paint['text-halo-width'] = 1.2
    paint['text-halo-blur'] = 0.4
    if lid.startswith(('water_name', 'waterway')):
        paint['text-color'] = p['water_label']
    elif lid.startswith('highway-name'):
        paint['text-color'] = p['label_soft']
    elif lid.startswith(('highway-shield', 'road_shield')):
        # The shields keep their sprite and ink: a road number reads the same on any map.
        paint.pop('text-halo-color', None)
        paint.pop('text-halo-width', None)
        paint.pop('text-halo-blur', None)
    elif lid in ('label_state', 'label_other'):
        # Apple writes districts and regions in upright spaced capitals, never italic.
        paint['text-color'] = p['label_soft']
        layout['text-transform'] = 'uppercase'
        layout['text-font'] = ['Noto Sans Bold'] if lid == 'label_other' else ['Noto Sans Regular']
        layout['text-letter-spacing'] = 0.12
    elif lid.startswith('label_country'):
        paint['text-color'] = p['label_soft']
    else:
        paint['text-color'] = p['label']
        if lid in ('label_city', 'label_city_capital'):
            layout['text-font'] = ['Noto Sans Bold']


def urban_layer(p):
    """Apple paints a whole city in one built-up colour; Liberty fills only residential land, and the parks' green
    showed between the blocks. Commercial, retail, industrial and railway land take the city's colour too."""
    return {
        'id': 'landuse_urban', 'type': 'fill', 'source': 'openmaptiles', 'source-layer': 'landuse',
        'filter': ['match', ['get', 'class'], ['commercial', 'retail', 'industrial', 'railway', 'garages', 'construction',
                                               'quarry', 'military', 'kindergarten', 'college', 'university', 'neighbourhood',
                                               'suburb', 'quarter'], True, False],
        'paint': {'fill-color': p['urban'], 'fill-opacity': ['interpolate', ['linear'], ['zoom'], 6, 0.7, 10, 1]},
    }


def build(theme):
    style = json.loads(SOURCE.read_text())
    p = PALETTES[theme]
    style['name'] = f'TripTrack {theme}'
    style.pop('id', None)
    style['metadata'] = {'triptrack:source': 'OpenFreeMap Liberty, recoloured after Apple Maps (tools/mapstyle)'}
    style['sources'].pop('ne2_shaded', None)
    layers = []
    for layer in style['layers']:
        if layer['id'] in DROP:
            continue
        layer = copy.deepcopy(layer)
        restyle(layer, p)
        if not layer.get('layout'):
            layer.pop('layout', None)
        layers.append(layer)
        if layer['id'] == 'landuse_residential':
            layers.append(urban_layer(p))
    style['layers'] = layers
    return style


if __name__ == '__main__':
    OUT.mkdir(parents=True, exist_ok=True)
    for theme in PALETTES:
        path = OUT / f'triptrack-{theme}.json'
        path.write_text(json.dumps(build(theme), ensure_ascii=False, separators=(',', ':')) + '\n')
        print('wrote', path.relative_to(ROOT), len(build(theme)['layers']), 'layers')
