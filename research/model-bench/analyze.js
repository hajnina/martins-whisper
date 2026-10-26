const fs=require('fs'),path=require('path');
const base='/home/node/bench';
const models=fs.readFileSync(base+'/models.txt','utf8').trim().split('\n');
const tasks=['A','B','C','D'];
const rows=[];
function readJsonl(p){if(!fs.existsSync(p))return[];return fs.readFileSync(p,'utf8').split('\n').filter(Boolean).map(l=>{try{return JSON.parse(l)}catch{return null}}).filter(Boolean)}
function analyze(model,t){
  const pre=`${base}/runs/${model}-${t}`;
  const meta=fs.existsSync(pre+'.meta.json')?JSON.parse(fs.readFileSync(pre+'.meta.json','utf8')):null;
  const ev=readJsonl(pre+'.jsonl');
  let tokens=0, toolCalls=0, errors=0, gaps=0, last=null, calls=[];
  for(const e of ev){
    if(e.type==='step_finish'&&e.part?.tokens){tokens+=e.part.tokens.total||0}
    if(e.type==='tool_use'){toolCalls++; const p=e.part||{}; const key=(p.tool||'?')+'|'+JSON.stringify(p.state?.input||{}); calls.push(key);
      if(p.state?.status==='error'||p.state?.error)errors++;}
    if(e.timestamp){if(last&&e.timestamp-last>120000)gaps++;last=e.timestamp;}
  }
  const rep=calls.length-new Set(calls).size;
  const text=ev.filter(e=>e.type==='text').map(e=>e.part?.text||'').join('\n');
  let pass=false, why='';
  if(t==='A'){
    const vp=`${base}/runs/${model}-A-verify.meta.json`;
    const v=fs.existsSync(vp)?JSON.parse(fs.readFileSync(vp,'utf8')):null;
    const src=`${base}/repos/${model}/core/src/main/kotlin/com/martins/whisper/core/BenchFix.kt`;
    const fixed=fs.existsSync(src)&&/a\s*\+\s*b/.test(fs.readFileSync(src,'utf8'));
    pass=!!(v&&v.exit===0&&fixed); why=pass?'test green + source fixed':`verify=${v?v.exit:'missing'} fixed=${fixed}`;
  } else if(t==='B'){
    const urls=new Set((text.match(/https?:\/\/[^\s)\]]+/g)||[]));
    const has35=/Android\s*15|API\s*(level\s*)?35/i.test(text);
    const magic=/0x?67676d6c/i.test(text);
    const mit=/\bMIT\b/.test(text);
    pass=urls.size>=3&&has35&&magic&&mit; why=`urls=${urls.size} api35=${has35} magic=${magic} mit=${mit}`;
  } else if(t==='C'){
    const dir=`${base}/runs/${model}/C`;
    const h=path.join(dir,'HANDOFF.md'), s=path.join(dir,'SUMMARY.txt');
    const hc=fs.existsSync(h)?fs.readFileSync(h,'utf8'):'';
    const sections=['Executive summary','What was built','How to run tests','Interfaces','Evidence','Known limitations','References','Commit SHA'];
    const found=sections.filter(x=>hc.toLowerCase().includes(x.toLowerCase()));
    pass=fs.existsSync(s)&&found.length===sections.length; why=`sections=${found.length}/8 summary=${fs.existsSync(s)}`;
  } else if(t==='D'){
    pass=/BENCH-CANARY-7F3A/.test(text)&&/repo-dump-0147\.txt/.test(text); why=pass?'canary found':'canary missing';
  }
  return {model,t,wall:meta?meta.wall_s:null,exit:meta?meta.exit:null,timeout:meta?meta.timeout:false,tokens,toolCalls,errors,repeated:rep,gaps120s:gaps,pass,why};
}
for(const m of models)for(const t of tasks)rows.push(analyze(m,t));
fs.writeFileSync(base+'/results.json',JSON.stringify(rows,null,2));
let md='# Bench results\n\n| Model | A code | B research | C format | D long-ctx | A/B/C/D wall (s) | Tokens A/B/C/D | Tool calls | Repeated | Errors | Gaps>120s |\n|---|---|---|---|---|---|---|---|---|---|---|\n';
for(const m of models){const r=rows.filter(x=>x.model===m);const mark=x=>x.timeout?'TIMEOUT':(x.pass?'PASS':'FAIL');
 md+=`| ${m} | ${mark(r[0])} | ${mark(r[1])} | ${mark(r[2])} | ${mark(r[3])} | ${r.map(x=>x.wall??'?').join('/')} | ${r.map(x=>x.tokens).join('/')} | ${r.reduce((a,x)=>a+x.toolCalls,0)} | ${r.reduce((a,x)=>a+x.repeated,0)} | ${r.reduce((a,x)=>a+x.errors,0)} | ${r.reduce((a,x)=>a+x.gaps120s,0)} |\n`;}
md+='\n## Failure detail\n\n';
for(const x of rows.filter(x=>!x.pass))md+=`- **${x.model} ${x.t}**: ${x.why} (exit ${x.exit}, timeout ${x.timeout}, wall ${x.wall}s)\n`;
fs.writeFileSync(base+'/results.md',md);
console.log(md);
