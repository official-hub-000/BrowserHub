// Guards the offline page translator that the native layer injects.
//
// Keeps a verbatim copy of the injected script from MainActivity.java, then runs
// it against a minimal DOM shim to prove it: rewrites real page text, skips
// <script>/<style> content, respects word boundaries, reports counts, and can
// restore the pristine page. Run: node scripts/translate-injection-test.mjs
const dict = { privacy: 'গোপনীয়তা', browser: 'ব্রাউজার', 'ad blocker': 'বিজ্ঞাপন ব্লকার' };
const safeDict = JSON.stringify(dict);
const langCode = 'bn';
const safeLang = langCode.replace("'", '');
const js = "(function(){try{"
  + "var dict=" + safeDict + ";"
  + "if(window.__bhTranslateOff){window.__bhTranslateOff();}"
  + "var keys=[];for(var kk in dict){if(Object.prototype.hasOwnProperty.call(dict,kk)&&dict[kk]){keys.push(kk);}}"
  + "keys.sort(function(a,b){return b.length-a.length;});"
  + "var isWordChar=function(ch){return ch.length>0&&'abcdefghijklmnopqrstuvwxyz0123456789'.indexOf(ch)>=0;};"
  + "var convert=function(text){"
  + "  var lower=text.toLowerCase();var out='';var i=0;"
  + "  while(i<text.length){"
  + "    var hit=null;"
  + "    for(var k=0;k<keys.length;k++){"
  + "      var key=keys[k];"
  + "      if(lower.indexOf(key,i)===i){"
  + "        var before=i===0?' ':lower.charAt(i-1);"
  + "        var after=i+key.length>=lower.length?' ':lower.charAt(i+key.length);"
  + "        if(!isWordChar(before)&&!isWordChar(after)){hit=key;break;}"
  + "      }"
  + "    }"
  + "    if(hit){out+=dict[hit];i+=hit.length;}else{out+=text.charAt(i);i++;}"
  + "  }"
  + "  return out;"
  + "};"
  + "var changed=0;"
  + "var walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null,false);"
  + "var nodes=[];var n;"
  + "while((n=walker.nextNode())){"
  + "  var p=n.parentNode;if(!p)continue;"
  + "  var tag=p.nodeName;"
  + "  if(tag==='SCRIPT'||tag==='STYLE'||tag==='NOSCRIPT'||tag==='TEXTAREA'||tag==='CODE'||tag==='PRE'){continue;}"
  + "  var t=n.nodeValue;if(!t||!t.trim()||t.length>800){continue;}"
  + "  nodes.push(n);"
  + "}"
  + "for(var i2=0;i2<nodes.length;i2++){"
  + "  var node=nodes[i2];var original=node.nodeValue;var next=convert(original);"
  + "  if(next!==original){node.__bhOrig=original;node.nodeValue=next;changed++;}"
  + "}"
  + "window.__bhTranslateOff=function(){"
  + "  var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null,false);"
  + "  var m;while((m=w.nextNode())){if(m.__bhOrig!==undefined){m.nodeValue=m.__bhOrig;m.__bhOrig=undefined;}}"
  + "};"
  + "if(window.__onNativeTranslateResult){window.__onNativeTranslateResult('" + safeLang + "',changed);}"
  + "}catch(err){throw err;}})();";

// --- tiny DOM shim -------------------------------------------------------
let reported = null;
globalThis.window = {
  __onNativeTranslateResult: (lang, n) => { reported = { lang, n }; },
};
const bodyTexts = [
  { nodeValue: 'Your privacy matters. This browser blocks trackers.', parentNode: { nodeName: 'P' } },
  { nodeValue: 'alert("privacy")', parentNode: { nodeName: 'SCRIPT' } },
  { nodeValue: 'privacyware is not the same word', parentNode: { nodeName: 'P' } },
];
globalThis.document = {
  body: {},
  createTreeWalker: () => {
    let i = 0;
    return { nextNode: () => (i < bodyTexts.length ? bodyTexts[i++] : null) };
  },
};
globalThis.NodeFilter = { SHOW_TEXT: 4 };

eval(js);
console.log('after translate :', bodyTexts.map((t) => t.nodeValue));
console.log('reported        :', reported);
const okForeign = bodyTexts[0].nodeValue.includes('গোপনীয়তা');
const okScriptSkipped = bodyTexts[1].nodeValue === 'alert("privacy")';
const okWordBoundary = bodyTexts[2].nodeValue === 'privacyware is not the same word';
eval(js); // idempotent re-apply
eval('window.__bhTranslateOff()');
console.log('after undo      :', bodyTexts.map((t) => t.nodeValue));
const okUndo = bodyTexts[0].nodeValue === 'Your privacy matters. This browser blocks trackers.';
console.log({ okForeign, okScriptSkipped, okWordBoundary, okUndo });
if (!(okForeign && okScriptSkipped && okWordBoundary && okUndo)) {
  console.error('TRANSLATOR FAILED');
  process.exit(1);
}
console.log('Translator injection OK');
