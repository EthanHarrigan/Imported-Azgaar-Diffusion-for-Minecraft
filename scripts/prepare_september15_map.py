"""Build the reviewed southern Sand Sea import from the unmodified Azgaar export."""
import json
from pathlib import Path
from PIL import Image, ImageDraw
import numpy as np
from scipy import ndimage

downloads = Path(r'C:\Users\ethan\Downloads')
source = downloads / 'Ceteviles Full 2026-09-15-23-05.json'
destination = downloads / 'Ceteviles Full 2026-09-15-23-05 Sand-Sea-ready.json'
old = json.loads((downloads / 'Ceteviles Full 2026-09-12-20-07 Sand-Sea-ready.json').read_text(encoding='utf-8-sig'))
data = json.loads(source.read_text(encoding='utf-8-sig'))
# Screenshot is a 2560-wide map viewport plus a footer; use equal X/Y scaling,
# not the screenshot height (which includes the footer) as the map height.
outlines = [[(1057,716),(1080,714),(1107,723),(1135,721),(1167,729),(1188,750),(1200,768),(1190,780),(1173,783),(1155,778),(1135,780),(1107,790),(1084,796),(1065,804),(1063,791),(1070,773),(1073,759),(1062,750),(1058,737)],
            [(1267,709),(1266,732),(1275,739),(1300,738),(1326,746),(1335,758),(1330,775),(1321,791),(1318,802),(1333,816),(1354,812),(1378,808),(1396,806),(1408,787),(1417,769),(1408,753),(1395,742),(1377,738),(1355,729),(1335,724),(1313,715),(1292,710),(1278,715)]]
vertices={v['i']:v['p'] for v in data['pack']['vertices']}
colors={b['i']:b['color'] for b in data['pack']['biomes']}
reference=Image.open(r'C:\Users\ethan\AppData\Local\Temp\codex-clipboard-6ffdf54d-e5e0-4582-8308-3471998f175f.png')
native=Image.new('RGB',(2560,1317),'#466eab'); painter=ImageDraw.Draw(native)
for c in data['pack']['cells']:
    painter.polygon([tuple(vertices[v]) for v in c['v']],fill=colors[c['biome']])
centers=[]
for image in (reference,native):
    mask=np.all(np.array(image)[:,:,:3]==[213,231,235],axis=2)
    labels,_=ndimage.label(mask);sizes=np.bincount(labels.ravel())
    ids=np.argsort(sizes[1:])[-9:][::-1]+1
    centers.append(np.array(ndimage.center_of_mass(mask,labels,ids)))
sx,tx=np.polyfit(centers[0][:,1],centers[1][:,1],1)
sy,ty=np.polyfit(centers[0][:,0],centers[1][:,0],1)
error=np.max(np.abs(centers[0]*[sy,sx]+[ty,tx]-centers[1]))
assert error<2.5,('Screenshot registration mismatch',error)
# The screenshot was slightly zoomed and panned. Match nine ice landmasses to
# source polygons instead of assuming screen pixels are export coordinates.
polygons = [[(x*(2558/2048)*sx+tx,y*(1345/1077)*sy+ty) for x,y in p] for p in outlines]
def inside(point, polygon):
    x,y = point
    hit = False
    for (ax,ay),(bx,by) in zip(polygon,polygon[1:]+polygon[:1]):
        if (ay>y)!=(by>y) and x<(bx-ax)*(y-ay)/(by-ay)+ax:
            hit = not hit
    return hit

selected = []
counts = [0,0]
for cell in data['pack']['cells']:
    # Keep lakes and the authored forest/grassland enclaves. Both warm and cold
    # arid cells inside the user's southern outlines can form the sand province.
    if cell['h'] < 20 or cell['biome'] not in (1,2):
        continue
    for i,p in enumerate(polygons):
        if inside(cell['p'],p):
            selected.append(cell['i']); counts[i]+=1; cell['biome']=13; break
assert min(counts)>5,counts
biome = dict(old['pack']['biomes'][-1])
assert biome['i']==13 and biome['name']=='Sand Sea'
data['pack']['biomes'].append(biome)
data['terrainDiffusion'] = {
    'formatVersion':2,
    'biomeProfiles':old['terrainDiffusion']['biomeProfiles'],
    'summitAnchors':old['terrainDiffusion']['summitAnchors'],
    'selection':{'original':source.name,'cells':selected,'sourcePolygons':polygons,
                 'screenshotRegistration':{'scaleX':sx,'translateX':tx,'scaleY':sy,'translateY':ty,'maxLandmarkError':error},
                 'rule':'arid land cell centers inside southern marked outlines; water, forests and grasslands preserved'}
}
original = json.loads(source.read_text(encoding='utf-8-sig'))
assert data['grid']==original['grid']
assert data['pack']['vertices']==original['pack']['vertices']
assert data['pack']['rivers']==original['pack']['rivers']
for a,b in zip(data['pack']['cells'],original['pack']['cells']):
    assert {k:v for k,v in a.items() if k!='biome'}=={k:v for k,v in b.items() if k!='biome'}
    if a['i'] not in selected: assert a==b
assert not any(c['biome']==13 and not any(inside(c['p'],p) for p in polygons) for c in data['pack']['cells'])
destination.write_text(json.dumps(data,separators=(',',':'),ensure_ascii=False),encoding='utf-8')

# Standalone source-data map, not an edited screenshot or a Minecraft render.
image=Image.new('RGB',(2560,1317),'#466eab'); draw=ImageDraw.Draw(image)
vertices={v['i']:v['p'] for v in data['pack']['vertices']}
colors={b['i']:b['color'] for b in data['pack']['biomes']}
for c in data['pack']['cells']:
    points=[tuple(vertices[v]) for v in c['v']]
    draw.polygon(points,fill=colors[c['biome']])
for p in polygons: draw.line(p+[p[0]],fill='#e12626',width=4)
image.save(downloads/'Ceteviles September15 Sand Sea selection.png')
print(json.dumps({'output':str(destination),'selectedCells':counts,'sourceUnchanged':True},indent=2))
