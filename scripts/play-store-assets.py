# Play store graphics from the CI screenshots: unzip android-screens.zip (android-android-v2-latest release) and pass that folder.
#   python3 scripts/play-store-assets.py <unzipped-screens-dir>   -> docs/play/assets/{phone-01..08,feature-graphic}.png
import os, subprocess, sys
import tempfile; SP=tempfile.mkdtemp()
Z=os.path.abspath(sys.argv[1])
OUT=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','docs','play','assets')
LOGO=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','apps','web','public','nextband','logo-on-teal.svg')
FONTS='<link href="https://fonts.googleapis.com/css2?family=Hanken+Grotesk:wght@500;600&family=Newsreader:opsz,wght@6..72,500&display=block" rel="stylesheet">'
SHOTS=[
 ("light-result-writing-Overview","A band score for every criterion"),
 ("light-result-writing-Essay","Every mistake marked in your essay"),
 ("light-live-intro","Talk to a live AI examiner"),
 ("light-result-speaking-Fluency","See your pace, pauses and fluency"),
 ("light-lr-listening-exam-Playing","Listening and Reading, timed like the exam"),
 ("light-lr-result-Overview","Find out why you lost each mark"),
 ("light-review-Question","Drill the mistakes you actually make"),
 ("light-streak","A few minutes a day, every day"),
]
CSS='''*{margin:0;box-sizing:border-box}html,body{width:%dpx;height:%dpx;overflow:hidden;background:#0F766E}
body{font-family:"Hanken Grotesk",sans-serif;color:#fff;position:relative}
h1{font-family:Newsreader,serif;font-weight:500;letter-spacing:-0.02em;text-wrap:balance}
.phone{position:absolute;border-radius:56px;overflow:hidden;border:14px solid #0B3B37;background:#0B3B37;box-shadow:0 40px 80px rgba(0,0,0,.28)}
.phone img{display:block;width:100%%;border-radius:42px}'''
def render(html, name, w, h):
    f=f'{SP}/store-{name}.html'; open(f,'w').write(html)
    subprocess.run(['/usr/bin/google-chrome-stable','--headless=new','--disable-gpu','--hide-scrollbars','--virtual-time-budget=8000',f'--window-size={w},{h}',f'--screenshot={OUT}/{name}.png',f'file://{f}'],check=True,capture_output=True)
for i,(shot,cap) in enumerate(SHOTS,1):
    W,H=1080,1920 # Play: phone screenshots must be 9:16
    html=f'''<!doctype html><html><head><meta charset="utf-8">{FONTS}<style>{CSS%(W,H)}
h1{{position:absolute;left:90px;right:90px;top:120px;font-size:84px;line-height:1.08;text-align:center}}
.phone{{left:110px;width:860px;top:430px}}</style></head><body>
<h1>{cap}</h1><div class="phone"><img src="file://{Z}/{shot}.png"></div></body></html>'''
    render(html,f'phone-{i:02d}',W,H)
W,H=1024,500
html=f'''<!doctype html><html><head><meta charset="utf-8">{FONTS}<style>{CSS%(W,H)}
.logo{{position:absolute;left:72px;top:150px;height:64px}}
h1{{position:absolute;left:72px;top:250px;width:470px;font-size:46px;line-height:1.12}}
.phone{{left:620px;width:330px;top:56px;border-width:8px;border-radius:34px}}.phone img{{border-radius:26px}}
</style></head><body>
<img class="logo" src="file://{LOGO}"><h1>IELTS practice with honest band scores</h1>
<div class="phone"><img src="file://{Z}/light-result-writing-Overview.png"></div></body></html>'''
render(html,'feature-graphic',W,H)
print('ok')
