"""Install the validated local JAR with Modrinth's content store and file inventory."""
import hashlib
import json
from pathlib import Path
import shutil
import sqlite3
import subprocess
import time
import uuid
import zipfile

repo=Path(__file__).resolve().parents[1]
app=Path(r'C:\Users\ethan\AppData\Roaming\ModrinthApp')
profile=app/'profiles'/'Fabric 1.21.11'
instance='local:67cf836d-4f8a-44b6-b425-f5e9238e1de2'
name='terrain-diffusion-mc-2.2.0-blueprint.37-windows+1.21.11.jar'
jar=repo/'build'/'libs'/name
payload=jar.read_bytes()
with zipfile.ZipFile(jar) as z:
    assert z.testzip() is None
    assert json.loads(z.read('fabric.mod.json'))['version']=='2.2.0-blueprint.37-windows+1.21.11'
sha1=hashlib.sha1(payload).hexdigest();sha512=hashlib.sha512(payload).hexdigest()
sha256=hashlib.sha256(payload).hexdigest()
command="@(Get-CimInstance Win32_Process | Where-Object {$_.Name -match '^java(w)?\\.exe$' -and $_.CommandLine -match 'KnotClient|--gameDir'}).Count"
assert subprocess.check_output(['powershell','-NoProfile','-Command',command],text=True).strip()=='0','Close Minecraft first'
db=sqlite3.connect(app/'app.db',timeout=15);db.row_factory=sqlite3.Row
rows=db.execute('SELECT * FROM instance_files WHERE instance_id=? AND file_name LIKE ? AND enabled=1',(instance,'terrain-diffusion-mc%')).fetchall()
assert len(rows)==1, [dict(r) for r in rows]
old=dict(rows[0]);assert 'blueprint.35-' in old['file_name'],old
old_file=profile/old['relative_path'];disabled=old_file.with_name(old_file.name+'.disabled')
assert old_file.exists() and not disabled.exists()
assert hashlib.sha1(old_file.read_bytes()).hexdigest()==old['sha1']
backup=profile/'mod-backups'/'blueprint37-launcher-backup.json'
backup.parent.mkdir(exist_ok=True)
assert not backup.exists(),'Existing installation backup; inspect rather than overwrite'
backup.write_text(json.dumps(old,indent=2),encoding='utf-8')
target=profile/'mods'/name;stage=target.with_name(name+'.next-disabled')
assert not target.exists() and not stage.exists()
shutil.copy2(jar,stage)
blob=app/'store'/'content'/'objects'/sha512[:2]/sha512
blob.parent.mkdir(parents=True,exist_ok=True)
if not blob.exists(): shutil.copy2(jar,blob)
assert hashlib.sha512(blob.read_bytes()).hexdigest()==sha512
now=int(time.time());file_id='instance-file:'+str(uuid.uuid4())
renamed=False;activated=False
try:
    db.execute('BEGIN IMMEDIATE')
    old_file.rename(disabled);renamed=True
    stage.rename(target);activated=True
    db.execute('UPDATE instance_files SET enabled=0,missing=0,modified_at=? WHERE id=?',(now,old['id']))
    db.execute('INSERT INTO instance_files VALUES (?,?,?,?,?,?,?,?,?,?)',
               (file_id,instance,'mods/'+name,name,1,sha1,len(payload),0,now,now))
    db.execute("INSERT OR IGNORE INTO store_blobs (sha512,size,status,modified_as,created_at,last_used_at,verified_at,sources) VALUES (?,?,'ready',?,?,?,?,'[]')",
               (sha512,len(payload),blob.stat().st_mtime_ns,now,now,now))
    db.execute("INSERT INTO store_instance_files VALUES (?,?,'copy')",(file_id,sha512))
    db.commit()
except BaseException:
    db.rollback()
    if activated: target.rename(stage)
    if renamed: disabled.rename(old_file)
    raise
assert hashlib.sha512(target.read_bytes()).hexdigest()==sha512
active=list((profile/'mods').glob('terrain-diffusion-mc*.jar'))
assert active==[target],active
row=db.execute('SELECT f.*,s.blob_sha512,b.status FROM instance_files f JOIN store_instance_files s ON s.file_id=f.id JOIN store_blobs b ON b.sha512=s.blob_sha512 WHERE f.id=?',(file_id,)).fetchone()
assert row['enabled']==1 and row['missing']==0 and row['status']=='ready' and row['blob_sha512']==sha512
print(json.dumps({'installed':str(target),'sha256':sha256,'size':len(payload),'version':'blueprint.37','launcherVerified':True},indent=2))
