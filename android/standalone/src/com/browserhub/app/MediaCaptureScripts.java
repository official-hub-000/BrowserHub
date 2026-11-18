package com.browserhub.app;

/**
 * The two snippets injected into the real page for video capture.
 *
 * Kept in Java rather than being generated from TypeScript so the exact strings
 * that reach the page are auditable here, next to the code that sends them. Both
 * are wrapped in try/catch and are guarded against double-injection: a page
 * script that throws is a broken page, and this feature must never be the reason
 * a site stops working.
 *
 * HOOK runs as early as possible in the document so the network hooks exist
 * before the player starts. READBACK is idempotent and only reports.
 */
public final class MediaCaptureScripts {

    private MediaCaptureScripts() {}

    public static final String HOOK =
            "(function(){try{"
            + "if(window.__bhCaptureOn){return;}window.__bhCaptureOn=true;"
            + "if(!window.__bhMedia){window.__bhMedia=[];}"
            + "var MAX=120;"
            + "function isMedia(u,c){"
            + "  if(!u||typeof u!=='string')return false;"
            + "  if(u.indexOf('blob:')===0||u.indexOf('data:')===0)return false;"
            + "  var p=u.split('#')[0].toLowerCase();"
            + "  if(c&&/(video\\/|audio\\/|mpegurl|dash\\+xml)/.test(c))return true;"
            + "  return /\\.(mp4|webm|m4v|mov|mkv|m3u8|mpd|ts|m4s|m4a|mp3|aac|ogg|opus)(\\?|$)/.test(p);"
            + "}"
            + "function add(u,c,len){"
            + "  if(!isMedia(u,c))return;"
            + "  for(var i=0;i<window.__bhMedia.length;i++){if(window.__bhMedia[i].url===u){return;}}"
            + "  if(window.__bhMedia.length>=MAX){window.__bhMedia.shift();}"
            + "  window.__bhMedia.push({url:u,mime:c||'',bytes:len||0,at:Date.now()});"
            + "}"
            + "var open=XMLHttpRequest.prototype.open;"
            + "XMLHttpRequest.prototype.open=function(m,u){"
            + "  try{var self=this;"
            + "    this.addEventListener('loadend',function(){try{"
            + "      var ct=self.getResponseHeader('Content-Type')||'';"
            + "      var cl=parseInt(self.getResponseHeader('Content-Length')||'0',10)||0;"
            + "      add(self.responseURL||u,ct,cl);}catch(e){}});"
            + "    add(u,'',0);}catch(e){}"
            + "  return open.apply(this,arguments);};"
            + "var realFetch=window.fetch;"
            + "if(realFetch){window.fetch=function(input,init){"
            + "  try{var u=typeof input==='string'?input:(input&&input.url);add(u,'',0);"
            + "    var pr=realFetch.apply(this,arguments);"
            + "    if(pr&&pr.then){pr.then(function(r){try{if(r&&r.url){"
            + "      var ct=(r.headers&&r.headers.get&&r.headers.get('Content-Type'))||'';"
            + "      var cl=parseInt((r.headers&&r.headers.get&&r.headers.get('Content-Length'))||'0',10)||0;"
            + "      add(r.url,ct,cl);}}catch(e){}return r;})['catch'](function(e){throw e;});}"
            + "    return pr;}catch(e){return realFetch.apply(this,arguments);}};}"
            + "var vs=Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype,'src');"
            + "if(vs&&vs.set){Object.defineProperty(HTMLMediaElement.prototype,'src',{"
            + "  set:function(v){try{add(String(v),'','');}catch(e){}return vs.set.call(this,v);},"
            + "  get:vs.get});}"
            + "var vso=Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype,'srcObject');"
            + "if(vso&&vso.set){Object.defineProperty(HTMLMediaElement.prototype,'srcObject',{"
            + "  set:function(v){try{window.__bhUsesMse=true;}catch(e){}return vso.set.call(this,v);},"
            + "  get:vso.get});}"
            + "var so=URL.createObjectURL;"
            + "if(so){URL.createObjectURL=function(o){try{window.__bhUsesMse=true;}catch(e){}"
            + "  return so.apply(URL,arguments);};}"
            + "var sp=document.createElement.bind(document);"
            + "document.createElement=function(t){var el=sp.apply(document,arguments);"
            + "  try{if(String(t).toLowerCase()==='video'){"
            + "    el.addEventListener('loadedmetadata',function(){"
            + "      try{add(el.currentSrc||el.src,'','');}catch(e){}});}}catch(e){}"
            + "  return el;};"
            // Encrypted Media Extensions: asking for a key system IS the DRM
            // signal. Without this the flag could never be set, and the app
            // would offer a download guaranteed to produce junk.
            + "try{if(navigator.requestMediaKeySystemAccess){"
            + "  var rmk=navigator.requestMediaKeySystemAccess;"
            + "  navigator.requestMediaKeySystemAccess=function(){"
            + "    try{window.__bhDrmSeen=true;}catch(e){}"
            + "    return rmk.apply(navigator,arguments);};}}catch(e){}"
            + "}catch(e){}})();";

    public static final String READBACK =
            "(function(){try{"
            + "var out=[];var list=window.__bhMedia||[];"
            + "for(var i=0;i<list.length;i++){out.push({url:list[i].url,mime:list[i].mime,bytes:list[i].bytes});}"
            + "var vids=document.querySelectorAll('video');"
            + "for(var j=0;j<vids.length;j++){var v=vids[j];var s=v.currentSrc||v.src||'';"
            + "  if(s&&s.indexOf('blob:')!==0){out.push({url:s,mime:'',bytes:0,element:true});}}"
            + "var metas=document.querySelectorAll("
            + "'meta[property=\"og:video\"],meta[property=\"og:video:url\"],"
            + "meta[property=\"og:video:secure_url\"]');"
            + "for(var k=0;k<metas.length;k++){var c=metas[k].getAttribute('content')||'';"
            + "  if(c){out.push({url:c,mime:'',bytes:0,meta:true});}}"
            + "var drm=false;"
            + "try{drm=!!(navigator.requestMediaKeySystemAccess&&window.__bhDrmSeen);}catch(e){}"
            + "if(!drm){try{"
            + "  var dv=document.querySelectorAll('video');"
            + "  for(var m=0;m<dv.length;m++){"
            + "    var ds=dv[m].currentSrc||dv[m].src||'';"
            + "    if(ds.indexOf('blob:')===0||dv[m].mediaKeys){drm=true;break;}}"
            + "}catch(e){}}"
            + "return JSON.stringify({items:out,mse:!!window.__bhUsesMse,drm:drm});"
            + "}catch(e){return '{\"items\":[],\"mse\":false,\"drm\":false}';}})();";
}
