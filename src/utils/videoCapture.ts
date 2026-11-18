/**
 * Video capture — the honest core.
 *
 * Every "video downloader" app in this space (VidMate, Video Der, SnapTube)
 * works the same way: it sits inside a browser, watches the network requests the
 * page makes, and offers the ones that look like media. That mechanism is
 * legitimate to implement — it fetches a file the user can already see playing.
 *
 * What those apps will not tell you, and what this module refuses to hide:
 *
 *   1. A YouTube-style stream is usually not one file. The player asks for a
 *      manifest (HLS .m3u8 / DASH .mpd) and then pulls hundreds of small
 *      segments. Saving one segment gives you two seconds of video, not a
 *      movie. Merging them is a separate capability and is reported as such.
 *   2. Many URLs are signed and expire within minutes. A link captured while
 *      the video plays may already be dead when you tap download.
 *   3. Widevine/PlayReady (DRM) streams are encrypted on purpose. Nothing a
 *      browser can do will produce a playable file, and claiming otherwise is a
 *      lie.
 *   4. Cross-origin media cannot always be fetched. In the web build the
 *      browser's CORS rules block it; on Android the app's own downloader goes
 *      through the platform, which is not subject to CORS.
 *
 * So this module classifies what it found and states the truth per item. The UI
 * only ever offers a download it believes will actually work.
 */

export type MediaKind = 'file' | 'hls' | 'dash';

export type CaptureSource = 'network' | 'element' | 'meta';

export interface CapturedMedia {
  /** Absolute URL of the media file or manifest. */
  url: string;
  kind: MediaKind;
  /** Value of the request's Content-Type, when the page saw one. */
  mime: string;
  /** Length in bytes when known (Content-Length or an <video> duration guess). */
  bytes: number;
  /** Where it was found — the inspector shows this so nothing is mysterious. */
  source: CaptureSource;
  /** Human label, e.g. the page title or "video #2". */
  label: string;
  /** True when the URL carries obvious expiring-signature parameters. */
  signed: boolean;
}

/** The page's own view of whether it can play this itself. */
export interface PageConstraints {
  /** The page is playing through MediaSource / a `blob:` URL. */
  usesMse: boolean;
  /** The page requested an encrypted media key system (Widevine etc.). */
  drm: boolean;
  /** The page reported a same-origin policy that will block reading the bytes. */
  crossoriginBlocked: boolean;
}

export type DownloadVerdict =
  | 'ready'
  | 'cross-origin-blocked'
  | 'needs-merge'
  | 'drm-protected'
  | 'live-stream'
  /** A stream that can be joined into one file by the native engine. */
  | 'mergeable';

export interface MediaAssessment {
  media: CapturedMedia;
  verdict: DownloadVerdict;
  /** One plain sentence a non-technical user understands. */
  reason: string;
  /** Whether the download button should be offered at all. */
  actionable: boolean;
}

const MANIFEST_HINTS = ['.m3u8', '.mpd'];
const VIDEO_EXT = ['.mp4', '.webm', '.m4v', '.mov', '.mkv', '.ts', '.m4s'];
const AUDIO_EXT = ['.m4a', '.mp3', '.aac', '.ogg', '.opus'];

/** Strips query and fragment so extension checks look at the path only. */
export function pathOf(url: string): string {
  const noHash = url.split('#')[0];
  const q = noHash.indexOf('?');
  return (q >= 0 ? noHash.slice(0, q) : noHash).toLowerCase();
}

export function hostOf(url: string): string {
  const m = /^[a-z]+:\/\/([^/?#]+)/i.exec(url);
  return m ? m[1].toLowerCase() : '';
}

/** A URL that carries a signature/token and will stop working soon. */
export function looksSigned(url: string): boolean {
  return /[?&](expire|expires|exp|sig|signature|token|hdnts|policy|key-pair-id|hmac|x-amz-|sd=|ip=)/i.test(
    url
  );
}

/**
 * Classify a URL by what it points at. Returns null when it is not media at
 * all, so the inspector never fills up with page assets.
 */
export function classifyMediaUrl(
  url: string,
  mime: string,
  contentLength = 0
): CapturedMedia | null {
  if (!url || !/^https?:\/\//i.test(url)) return null;
  const path = pathOf(url);
  const lowerMime = (mime || '').toLowerCase();

  const isManifest = MANIFEST_HINTS.some((h) => path.includes(h)) || lowerMime.includes('mpegurl');
  if (isManifest) {
    const kind: MediaKind = path.includes('.mpd') || lowerMime.includes('dash') ? 'dash' : 'hls';
    return {
      url,
      kind,
      mime: lowerMime,
      bytes: contentLength,
      source: 'network',
      label: kind === 'hls' ? 'HLS stream' : 'DASH stream',
      signed: looksSigned(url),
    };
  }

  if (lowerMime.startsWith('video/')) {
    return {
      url,
      kind: 'file',
      mime: lowerMime,
      bytes: contentLength,
      source: 'network',
      label: 'Video file',
      signed: looksSigned(url),
    };
  }
  if (lowerMime.startsWith('audio/')) {
    return {
      url,
      kind: 'file',
      mime: lowerMime,
      bytes: contentLength,
      source: 'network',
      label: 'Audio track',
      signed: looksSigned(url),
    };
  }

  /*
   * YouTube's media. googlevideo serves from /videoplayback with no extension at
   * all, so the extension checks below never matched it and every YouTube capture
   * was silently dropped even when the capture itself worked.
   *
   * `mime=audio` in the query means an audio-only track; separate tracks are
   * normal on YouTube and are exactly what the merge step expects.
   */
  const lowerUrl = url.toLowerCase();
  if (lowerUrl.includes('/videoplayback')) {
    const audioOnly = /[?&]mime=audio/.test(lowerUrl) || /[?&]itag=(140|139|141|249|250|251|256|258)(&|$)/.test(lowerUrl);
    return {
      url,
      kind: 'file',
      mime: audioOnly ? 'audio/mp4' : lowerMime || 'video/mp4',
      bytes: contentLength,
      source: 'network',
      label: audioOnly ? 'YouTube audio track' : 'YouTube video track',
      signed: looksSigned(url),
    };
  }

  const hasExt = VIDEO_EXT.some((e) => path.endsWith(e)) || AUDIO_EXT.some((e) => path.endsWith(e));
  if (hasExt) {
    return {
      url,
      kind: 'file',
      mime: lowerMime || 'video/mp4',
      bytes: contentLength,
      source: 'network',
      label: AUDIO_EXT.some((e) => path.endsWith(e)) ? 'Audio file' : 'Video file',
      signed: looksSigned(url),
    };
  }
  return null;
}

/** De-duplicates a capture list by URL, keeping the entry with the most detail. */
export function dedupeMedia(items: CapturedMedia[]): CapturedMedia[] {
  const byUrl = new Map<string, CapturedMedia>();
  for (const item of items) {
    const prior = byUrl.get(item.url);
    if (!prior) {
      byUrl.set(item.url, item);
      continue;
    }
    byUrl.set(item.url, {
      ...prior,
      // Prefer a known size and a richer label.
      bytes: Math.max(prior.bytes, item.bytes),
      label: prior.label.length >= item.label.length ? prior.label : item.label,
      mime: prior.mime || item.mime,
    });
  }
  // Biggest files first: that is almost always the real video, not a tracker.
  return [...byUrl.values()].sort((a, b) => b.bytes - a.bytes);
}

/**
 * Decide whether a captured item can become a file on this device.
 *
 * `nativeDownloader` is true inside the Android app, where the download runs
 * through the platform rather than the page's fetch, so the page's CORS rules do
 * not apply. In the web build that is false and cross-origin media is honestly
 * reported as blocked.
 */
export function assessMedia(
  media: CapturedMedia,
  page: PageConstraints,
  pageUrl: string,
  nativeDownloader: boolean,
  options: { canMergeStreams?: boolean } = {}
): MediaAssessment {
  const canMerge = Boolean(options.canMergeStreams) && nativeDownloader;

  if (media.kind === 'hls') {
    if (canMerge) {
      return {
        media,
        verdict: 'mergeable',
        actionable: true,
        reason:
          'A stream, not a file. BrowserHub will fetch every part and join them into one video. ' +
          'Encrypted streams it cannot decrypt are refused before they start.',
      };
    }
    return {
      media,
      verdict: 'needs-merge',
      actionable: false,
      reason:
        'This is a stream playlist, not a video file. Saving it gives you a small text file. ' +
        'Joining the parts needs the Android build, which does it natively.',
    };
  }

  if (media.kind === 'dash') {
    if (canMerge) {
      return {
        media,
        verdict: 'mergeable',
        actionable: true,
        reason:
          'A DASH stream. BrowserHub fetches the picture and the sound as separate tracks and ' +
          'joins both into one video.',
      };
    }
    return {
      media,
      verdict: 'needs-merge',
      actionable: false,
      reason:
        'This is a DASH manifest, a list of parts rather than a video file. Joining it needs the ' +
        'Android build, which does it natively.',
    };
  }

  if (page.drm) {
    return {
      media,
      verdict: 'drm-protected',
      actionable: false,
      reason:
        'This page plays the video through DRM (Widevine). The bytes are encrypted on purpose, ' +
        'so no browser-side download can produce a playable file. That cannot be worked around ' +
        'in an app. Any downloader claiming otherwise is selling you something fake.',
    };
  }

  const crossOrigin = hostOf(media.url) !== hostOf(pageUrl);
  if (crossOrigin && !nativeDownloader) {
    return {
      media,
      verdict: 'cross-origin-blocked',
      actionable: false,
      reason:
        'The video is served from a different host, and this web build is bound by the page\'s ' +
        'cross-origin rules, so it cannot read the bytes. The Android app downloads through the ' +
        'platform instead and is not affected by this.',
    };
  }

  return {
    media,
    verdict: 'ready',
    actionable: true,
    reason: crossOrigin
      ? 'Served from a separate media host; the Android downloader fetches it directly.'
      : media.signed
        ? 'Direct video file. This link is signed and expires, so download it now rather than later.'
        : 'Direct video file, ready to download.',
  };
}

/** A filename that will not collide with the last three downloads. */
export function suggestedFilename(media: CapturedMedia, pageTitle: string): string {
  const safeTitle =
    (pageTitle || 'video')
      .replace(/[\\/:*?"<>|]+/g, ' ')
      .replace(/\s+/g, ' ')
      .trim()
      .slice(0, 60) || 'video';
  const path = pathOf(media.url);
  const extMatch = /\.(mp4|webm|m4v|mov|mkv|m4a|mp3|aac|ogg|opus)$/.exec(path);
  const ext = extMatch ? extMatch[1] : media.mime.includes('webm') ? 'webm' : 'mp4';
  return `${safeTitle}.${ext}`;
}

/**
 * The script injected into the real page. It hooks XHR, fetch and the media
 * element setters *before* the player runs, so a video that streams through
 * MediaSource is still visible as a URL.
 *
 * Returns a JSON string so the native side can hand it straight back to the
 * shell without parsing it in Java.
 */
export function buildCaptureScript(): string {
  return `(function(){
  try{
    if(window.__bhCaptureOn){return;}
    window.__bhCaptureOn=true;
    if(!window.__bhMedia){window.__bhMedia=[];}
    var MAX=120;
    function isMedia(u,c){
      if(!u||typeof u!=='string')return false;
      if(u.indexOf('blob:')===0||u.indexOf('data:')===0)return false;
      var p=u.split('#')[0].toLowerCase();
      if(c&&/(video\\/|audio\\/|mpegurl|dash\\+xml)/.test(c))return true;
      return /\\.(mp4|webm|m4v|mov|mkv|m3u8|mpd|ts|m4s|m4a|mp3|aac|ogg|opus)(\\?|$)/.test(p);
    }
    function add(u,c,len){
      if(!isMedia(u,c))return;
      for(var i=0;i<window.__bhMedia.length;i++){
        if(window.__bhMedia[i].url===u){return;}
      }
      if(window.__bhMedia.length>=MAX){window.__bhMedia.shift();}
      window.__bhMedia.push({url:u,mime:c||'',bytes:len||0,at:Date.now()});
    }
    var open=XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open=function(m,u){
      try{
        var self=this;
        this.addEventListener('loadend',function(){
          try{
            var ct=self.getResponseHeader('Content-Type')||'';
            var cl=parseInt(self.getResponseHeader('Content-Length')||'0',10)||0;
            add(self.responseURL||u,ct,cl);
          }catch(e){}
        });
        add(u,'',0);
      }catch(e){}
      return open.apply(this,arguments);
    };
    var realFetch=window.fetch;
    if(realFetch){
      window.fetch=function(input,init){
        try{
          var u=typeof input==='string'?input:(input&&input.url);
          add(u,'',0);
          var p=realFetch.apply(this,arguments);
          if(p&&p.then){
            p.then(function(r){
              try{
                if(r&&r.url){
                  var ct=(r.headers&&r.headers.get&&r.headers.get('Content-Type'))||'';
                  var cl=parseInt((r.headers&&r.headers.get&&r.headers.get('Content-Length'))||'0',10)||0;
                  add(r.url,ct,cl);
                }
              }catch(e){}
              return r;
            })['catch'](function(e){return Promise.reject(e);});
          }
          return p;
        }catch(e){return realFetch.apply(this,arguments);}
      };
    }
    var vs=Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype,'src');
    if(vs&&vs.set){
      Object.defineProperty(HTMLMediaElement.prototype,'src',{
        set:function(v){try{add(String(v),'','');}catch(e){}return vs.set.call(this,v);},
        get:vs.get
      });
    }
    var vso=Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype,'srcObject');
    if(vso&&vso.set){
      Object.defineProperty(HTMLMediaElement.prototype,'srcObject',{
        set:function(v){try{window.__bhUsesMse=true;}catch(e){}return vso.set.call(this,v);},
        get:vso.get
      });
    }
    var so=URL.createObjectURL;
    if(so){URL.createObjectURL=function(o){try{window.__bhUsesMse=true;}catch(e){}return so.apply(URL,arguments);};}
    try{if(navigator.requestMediaKeySystemAccess){
      var rmk=navigator.requestMediaKeySystemAccess;
      navigator.requestMediaKeySystemAccess=function(){
        try{window.__bhDrmSeen=true;}catch(e){}
        return rmk.apply(navigator,arguments);};
    }}catch(e){}
    var sp=document.createElement.bind(document);
    document.createElement=function(t){
      var el=sp.apply(document,arguments);
      try{
        if(String(t).toLowerCase()==='video'){
          el.addEventListener('loadedmetadata',function(){try{add(el.currentSrc||el.src,'','');}catch(e){}});
        }
      }catch(e){}
      return el;
    };
  }catch(e){}
})();`;
}

/**
 * Reads back what the injection collected. Runs in the page, so it returns a
 * JSON string; the shell parses it.
 */
export function buildCaptureReadbackScript(): string {
  return `(function(){
  try{
    var out=[];
    var list=window.__bhMedia||[];
    for(var i=0;i<list.length;i++){out.push({url:list[i].url,mime:list[i].mime,bytes:list[i].bytes});}
    var vids=document.querySelectorAll('video');
    for(var j=0;j<vids.length;j++){
      var v=vids[j];
      var s=v.currentSrc||v.src||'';
      if(s&&s.indexOf('blob:')!==0){out.push({url:s,mime:'',bytes:0,element:true});}
    }
    var metas=document.querySelectorAll('meta[property="og:video"],meta[property="og:video:url"],meta[property="og:video:secure_url"]');
    for(var k=0;k<metas.length;k++){
      var c=metas[k].getAttribute('content')||'';
      if(c){out.push({url:c,mime:'',bytes:0,meta:true});}
    }
    var drm=false;
    try{drm=!!(navigator.requestMediaKeySystemAccess&&window.__bhDrmSeen);}catch(e){}
    if(!drm){try{
      var dv=document.querySelectorAll('video');
      for(var m=0;m<dv.length;m++){
        var ds=dv[m].currentSrc||dv[m].src||'';
        if(ds.indexOf('blob:')===0||dv[m].mediaKeys){drm=true;break;}}
    }catch(e){}}
    return JSON.stringify({items:out,mse:!!window.__bhUsesMse,drm:drm});
  }catch(e){return '{"items":[],"mse":false,"drm":false}';}
})();`;
}

/** Parse the readback into typed captures. Never throws on a bad payload. */
export function parseCaptures(raw: string): {
  items: CapturedMedia[];
  page: PageConstraints;
} {
  const empty = {
    items: [] as CapturedMedia[],
    page: { usesMse: false, drm: false, crossoriginBlocked: false },
  };
  if (!raw) return empty;
  let decoded = raw.trim();
  // evaluateJavascript hands back a JSON string literal, so unwrap then re-parse.
  try {
    if (decoded.startsWith('"') && decoded.endsWith('"')) decoded = JSON.parse(decoded);
  } catch {
    return empty;
  }
  let payload: any;
  try {
    payload = typeof decoded === 'string' ? JSON.parse(decoded) : decoded;
  } catch {
    return empty;
  }
  if (!payload || !Array.isArray(payload.items)) return empty;

  const items: CapturedMedia[] = [];
  for (const raw of payload.items) {
    if (!raw || typeof raw.url !== 'string') continue;
    const media = classifyMediaUrl(raw.url, raw.mime || '', Number(raw.bytes) || 0);
    if (!media) continue;
    if (raw.element) media.source = 'element';
    if (raw.meta) media.source = 'meta';
    items.push(media);
  }

  return {
    items: dedupeMedia(items),
    page: {
      usesMse: Boolean(payload.mse),
      drm: Boolean(payload.drm),
      crossoriginBlocked: false,
    },
  };
}

/** Total bytes across captures, for the "found N videos (M MB)" summary. */
export function summarise(items: CapturedMedia[]): { count: number; bytes: number } {
  return {
    count: items.length,
    bytes: items.reduce((sum, i) => sum + (i.bytes > 0 ? i.bytes : 0), 0),
  };
}
