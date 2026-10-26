const fs = require('fs'), path = require('path');
const NEW = '/work/orin/bench/runs';
const OLD = '/work/orin/wt/research/model-bench/runs';
const MODELS = ['deepseek-v4.1-flash-free','glm-5.3-flash-free','mimo-v2.6-flash-free','big-pickle','flint-40b-free','nemotron-3-ultra-free','nemotron-3.5-lightning-free','nemotron-3.5-super-vl-free','beam-free'];
const TASKS = ['A','B','C','D'];
const OUT_JSON = '/work/orin/wt/research/model-bench/results-all.json';
const OUT_MD = '/work/orin/wt/research/model-bench/results-all.md';

function readJsonl(p){ if(!fs.existsSync(p)) return []; return fs.readFileSync(p,'utf8').split('\n').filter(Boolean).map(l=>{try{return JSON.parse(l)}catch{return null}}).filter(Boolean); }
function meta(p){ try{ return JSON.parse(fs.readFileSync(p,'utf8')); }catch{ return null; } }
function pick(model,task){
  const nMeta = meta(path.join(NEW,`${model}-${task}.meta.json`));
  const oMeta = meta(path.join(OLD,`${model}-${task}.meta.json`));
  const nJ = path.join(NEW,`${model}-${task}.jsonl`);
  if (nMeta && fs.existsSync(nJ)) return {dir:NEW, m:nMeta, j:nJ, which:'new'};
  if (oMeta || fs.existsSync(path.join(OLD,`${model}-${task}.jsonl`))) return {dir:OLD, m:oMeta, j:path.join(OLD,`${model}-${task}.jsonl`), which:'recovered'};
  return null;
}
function reconC(jsonl){
  const files={}; const errs=[];
  for(const e of readJsonl(jsonl)){
    if(e.type==='error') errs.push(e.error?.data?.message || e.error?.name || 'error');
    if(e.type!=='tool_use') continue;
    const p=e.part||{}; const inp=p.state?.input||{};
    if(p.tool==='write' && inp.filePath) files[inp.filePath]=inp.content||'';
    if(p.tool==='edit' && inp.filePath && files[inp.filePath]!==undefined)
      files[inp.filePath]=files[inp.filePath].replace(inp.oldString||'', inp.newString||'');
  }
  return {files,errs};
}
const rows=[];
for(const model of MODELS) for(const task of TASKS){
  const p = pick(model,task);
  const row = {model,task,which:p?p.which:null,wall:null,exit:null,timeout:false,tokens:0,toolCalls:0,toolErrors:0,apiErrors:0,repeats:0,gaps120s:0,text:'',pass:false,blocked:false,why:'',apiMessages:[]};
  if(!p){ row.why='not run'; rows.push(row); continue; }
  if(p.m){ row.wall=p.m.wall_s; row.exit=p.m.exit; row.timeout=!!p.m.timeout; }
  const ev=readJsonl(p.j); let last=null; const calls=[];
  for(const e of ev){
    if(e.type==='step_finish' && e.part?.tokens) row.tokens += e.part.tokens.total||0;
    if(e.type==='error'){ row.apiErrors++; const m=e.error?.data?.message||e.error?.name||'error'; row.apiMessages.push(m); }
    if(e.type==='tool_use'){ row.toolCalls++; const pp=e.part||{}; calls.push((pp.tool||'?')+'|'+JSON.stringify(pp.state?.input||{})); if(pp.state?.status==='error'||pp.state?.error) row.toolErrors++; }
    if(e.type==='text') row.text += (e.part?.text||'')+'\n';
    if(e.timestamp){ if(last && e.timestamp-last>120000) row.gaps120s++; last=e.timestamp; }
  }
  row.repeats = calls.length - new Set(calls).size;
  const vm = meta(path.join(p.dir,`${model}-A-verify.meta.json`));
  const err = row.apiMessages.join('; ');
  if(task==='A'){
    if(row.tokens===0 && row.apiErrors>0){ row.blocked=true; row.why='blocked: provider ('+err+')'; }
    else { row.pass = !!(vm && vm.exit===0); row.why=`verify=${vm?vm.exit:'missing'} apiErr=${row.apiErrors}`; }
  } else if(task==='B'){
    const urls=new Set((row.text.match(/https?:\/\/[^\s)\]`>]+/g)||[]));
    const api15=/Android\s*15|API\s*(level\s*)?35/i.test(row.text);
    const magic=/67676d6c/i.test(row.text);
    const mit=/\bMIT\b/i.test(row.text);
    const done = p.m && p.m.exit===0;
    row.pass = !!(done && urls.size>=3 && api15 && magic && mit);
    if(!done && row.tokens===0) { row.blocked=true; row.why='blocked: provider/interrupted'; }
    else row.why=`urls=${urls.size} android15/api35=${api15} magic=${magic} mit=${mit} exit=${p.m?p.m.exit:'none'}${!p.m?' (no meta: interrupted)':''}`;
  } else if(task==='C'){
    const {files,errs}=reconC(p.j);
    const hk=Object.keys(files).find(k=>/HANDOFF\.md$/.test(k));
    const sk=Object.keys(files).find(k=>/SUMMARY\.txt$/.test(k));
    const txt=hk?files[hk]:'';
    const secs=['Executive summary','What was built','How to run tests','Interfaces','Evidence','Known limitations','References','Commit SHA'];
    const found=secs.filter(s=>txt.toLowerCase().includes(s.toLowerCase()));
    const done = p.m && p.m.exit===0;
    row.pass = !!(done && found.length>=7 && !!sk);
    if(!done && row.tokens===0) { row.blocked=true; row.why='blocked: provider/interrupted'; }
    else row.why=`sections=${found.length}/8 summary=${!!sk} exit=${p.m?p.m.exit:'none'}${errs.length?' apiErr='+errs.join(';'):''}`;
  } else if(task==='D'){
    const found=/BENCH-CANARY-7F3A/.test(row.text) && /repo-dump-0147\.txt/.test(row.text);
    const done = p.m && p.m.exit===0;
    row.pass = !!(done && found);
    if(!done && row.tokens===0) { row.blocked=true; row.why='blocked: provider/interrupted'; }
    else row.why=`canary=${found} exit=${p.m?p.m.exit:'none'}`;
  }
  // infra errors are not model verdicts
  const INFRA=/Endpoint is unavailable|Model is unavailable|Free usage exceeded|rate limit|429|ENOSPC|timeout/i;
  if (row.apiMessages.some(m=>INFRA.test(m))) { row.blocked=true; row.pass=false; row.why='blocked: provider ('+err+')'; }
  // manual grades where the regex over/under-reads the final answer
  const MANUAL = {
    'nemotron-3.5-super-vl-free|B': {pass:false, note:'PARTIAL: (a) withheld — source 1 unreachable; (b) correct'},
    'nemotron-3.5-lightning-free|C': {pass:true, note:'PASS content, but wrote outside its workdir (worktree + owner dir); containment risk'},
    'mimo-v2.6-flash-free|C': {pass:true, note:'PASS manually (8/8 with variant headings); naive heading match gave a false negative'}
  };
  const man = MANUAL[model+'|'+task];
  if (man) { if (man.pass!==undefined) row.pass=man.pass; if (man.note) { row.note=man.note; row.partial=/PARTIAL/.test(man.note); } }
  rows.push(row);
}
fs.writeFileSync(OUT_JSON, JSON.stringify(rows,null,2));
const mark = r => !r || r.which===null ? '—' : (r.blocked?'BLOCKED':(r.partial?'PARTIAL':(r.timeout?'TIMEOUT':(r.pass?'PASS':'FAIL'))));
let md = '# Model bench — measured results\n\n';
md += 'Tasks: A = fix failing Kotlin unit test (harness-verified); B = web research with 3 citations; C = strict HANDOFF.md template; D = find canary in ~200k-token pile. ';
md += '`—` = not run. BLOCKED = provider/infra, not a model verdict. Recovered = app PM runs 16:58–17:03Z; new = ORIN reruns.\n\n';
md += '| Model | A | B | C | D | wall A/B/C/D (s) | tokens A/B/C/D | tools | toolErr | apiErr | repeats |\n|---|---|---|---|---|---|---|---|---|---|---|\n';
for(const m of MODELS){
  const r=TASKS.map(t=>rows.find(x=>x.model===m&&x.task===t));
  md += `| ${m} | ${r.map(mark).join(' | ')} | ${r.map(x=>x&&x.which?x.wall??'—':'—').join('/')} | ${r.map(x=>x&&x.which?x.tokens:'—').join('/')} | ${r.reduce((a,x)=>a+((x&&x.which)?x.toolCalls:0),0)} | ${r.reduce((a,x)=>a+((x&&x.which)?x.toolErrors:0),0)} | ${r.reduce((a,x)=>a+((x&&x.which)?x.apiErrors:0),0)} | ${r.reduce((a,x)=>a+((x&&x.which)?x.repeats:0),0)} |\n`;
}
md += '\n## Detail\n\n';
for(const x of rows){
  if(x.which===null) continue;
  md += `- **${x.model} ${x.task}** ${mark(x)} — ${x.why}; source=${x.which}${x.note?" note="+x.note:""}; wall=${x.wall??'—'}s tokens=${x.tokens} tools=${x.toolCalls} repeats=${x.repeats} gaps120s=${x.gaps120s}\n`;
}
fs.writeFileSync(OUT_MD, md);
console.log(md);
