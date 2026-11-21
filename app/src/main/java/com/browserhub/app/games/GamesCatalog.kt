package com.browserhub.app.games

data class BrowserGame(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val accentHex: Long
)

object GamesCatalog {
    val games: List<BrowserGame> = listOf(
        BrowserGame("2048_neon", "2048 Neon", "Puzzle", "Slide & merge glowing number tiles to reach 2048", 0xFF00D4FF),
        BrowserGame("snake_cyber", "Cyber Snake", "Arcade", "Classic grid snake with OLED neon food & speed ramp", 0xFF10B981),
        BrowserGame("tetris_pulse", "Block Pulse", "Arcade", "Tap & swipe falling tetromino puzzle", 0xFFA855F7),
        BrowserGame("pong_duel", "Pong Duel AI", "Sports", "Fast touch paddle duel against reactive AI", 0xFF38BDF8),
        BrowserGame("brick_breaker", "Brick Breaker", "Arcade", "Bounce energy ball to shatter neon brick waves", 0xFFFB7185),
        BrowserGame("minesweeper", "Mine Sweeper", "Logic", "Reveal safe tiles and flag hidden mines", 0xFFF59E0B),
        BrowserGame("flappy_orbit", "Orbit Dash", "Reflex", "Tap thrust to glide through plasma gates", 0xFF22C55E),
        BrowserGame("memory_matrix", "Memory Matrix", "Brain", "Flip and pair encrypted symbol cards", 0xFF818CF8),
        BrowserGame("tic_tac_ai", "Tic-Tac-Toe AI", "Board", "Unbeatable minimax AI grid challenge", 0xFF06B6D4),
        BrowserGame("simon_tones", "Pulse Sequence", "Brain", "Repeat increasingly long light & pattern sequences", 0xFFEC4899),
        BrowserGame("reaction_tap", "Reflex Timer", "Speed", "Measure millisecond touch reaction latency", 0xFF14B8A6),
        BrowserGame("space_defender", "Star Defender", "Shooter", "Tap left/right to blast descending alien drones", 0xFFF43F5E),
        BrowserGame("connect_four", "Four-In-Row", "Board", "Drop discs vertically to beat the CPU", 0xFFFBBF24),
        BrowserGame("sliding_15", "15 Tile Slide", "Puzzle", "Order tiles 1 to 15 in minimal moves", 0xFF60A5FA),
        BrowserGame("color_flood", "Color Flood", "Logic", "Flood-fill the entire board within 22 moves", 0xFFC084FC),
        BrowserGame("whack_node", "Bug Zapper", "Reflex", "Tap popping tracker bugs before timer expires", 0xFFEF4444),
        BrowserGame("math_sprint", "Math Sprint", "Brain", "Solve rapid-fire arithmetic under a 30s clock", 0xFF34D399),
        BrowserGame("hangman_dev", "Code Hangman", "Word", "Guess tech & browser words before shield fails", 0xFF38BDF8)
    )

    fun buildPlayableGameHtml(game: BrowserGame): String {
        val accent = String.format("#%06X", game.accentHex and 0xFFFFFF)
        val engineJs = when (game.id) {
            "2048_neon" -> game2048Js()
            "snake_cyber" -> gameSnakeJs()
            "minesweeper" -> gameMinesweeperJs()
            "memory_matrix" -> gameMemoryJs()
            "tic_tac_ai" -> gameTicTacJs()
            "reaction_tap" -> gameReactionJs()
            else -> gameArcadeCanvasJs(game.title)
        }
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
              <title>${game.title}</title>
              <style>
                * { box-sizing: border-box; touch-action: manipulation; }
                body { margin: 0; background: #000000; color: #f8fafc; font-family: system-ui, sans-serif; display: flex; flex-direction: column; align-items: center; padding: 14px; min-height: 100vh; }
                .hdr { width: 100%; max-width: 420px; display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
                .title { font-size: 18px; font-weight: 700; color: $accent; }
                .score { font-family: monospace; background: #0a1017; border: 1px solid #1e293b; padding: 6px 12px; border-radius: 999px; font-size: 13px; }
                .card { width: 100%; max-width: 420px; background: #070b11; border: 1px solid #1c2736; border-radius: 16px; padding: 14px; }
                button.btn { background: $accent; color: #001018; border: none; border-radius: 10px; padding: 10px 16px; font-weight: 700; cursor: pointer; }
              </style>
            </head>
            <body>
              <div class="hdr">
                <div class="title">${game.title}</div>
                <div class="score" id="score">Score: 0</div>
              </div>
              <div class="card" id="stage"></div>
              <script>$engineJs</script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun game2048Js() = """
        var board = [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0], score = 0;
        function addTile(){ var e=[]; board.forEach(function(v,i){if(!v)e.push(i);}); if(e.length) board[e[Math.floor(Math.random()*e.length)]]=Math.random()<0.9?2:4; }
        function slide(r){ var a=r.filter(Boolean); for(var i=0;i<a.length-1;i++){ if(a[i]===a[i+1]){a[i]*=2; score+=a[i]; a[i+1]=0;} } a=a.filter(Boolean); while(a.length<4)a.push(0); return a; }
        function move(dir){
          var prev=board.join(',');
          for(var i=0;i<4;i++){
            var line=[];
            for(var j=0;j<4;j++) line.push(dir<2 ? board[i*4+j] : board[j*4+i]);
            if(dir===1||dir===3) line.reverse();
            line=slide(line);
            if(dir===1||dir===3) line.reverse();
            for(var j=0;j<4;j++) if(dir<2) board[i*4+j]=line[j]; else board[j*4+i]=line[j];
          }
          if(board.join(',')!==prev) addTile(); render();
        }
        function render(){
          document.getElementById('score').textContent='Score: '+score;
          var h='<div style="display:grid;grid-template-columns:repeat(4,1fr);gap:8px">';
          board.forEach(function(v){ h+='<div style="height:68px;display:flex;align-items:center;justify-content:center;border-radius:10px;font-weight:700;font-size:20px;background:'+(v?'#0d2636':'#0c121b')+';border:1px solid #1e293b">'+(v||'')+'</div>'; });
          h+='</div><div style="display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin-top:12px">'
            +'<button class="btn" onclick="move(0)">Left</button><button class="btn" onclick="move(2)">Up</button>'
            +'<button class="btn" onclick="move(3)">Down</button><button class="btn" onclick="move(1)">Right</button></div>';
          document.getElementById('stage').innerHTML=h;
        }
        addTile(); addTile(); render();
    """.trimIndent()

    private fun gameSnakeJs() = """
        var st=document.getElementById('stage');
        st.innerHTML='<canvas id="c" width="320" height="320" style="width:100%;background:#02060a;border-radius:12px"></canvas>'
          +'<div style="display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin-top:10px">'
          +'<button class="btn" onclick="setDir(-1,0)">Left</button><button class="btn" onclick="setDir(0,-1)">Up</button>'
          +'<button class="btn" onclick="setDir(0,1)">Down</button><button class="btn" onclick="setDir(1,0)">Right</button></div>';
        var ctx=document.getElementById('c').getContext('2d'), snake=[{x:8,y:8}], dir={x:1,y:0}, food={x:12,y:8}, score=0;
        window.setDir=function(x,y){ if(x!==-dir.x||y!==-dir.y) dir={x:x,y:y}; };
        setInterval(function(){
          var head={x:(snake[0].x+dir.x+16)%16, y:(snake[0].y+dir.y+16)%16};
          if(snake.some(function(s){return s.x===head.x&&s.y===head.y;})){ snake=[{x:8,y:8}]; score=0; }
          snake.unshift(head);
          if(head.x===food.x&&head.y===food.y){ score+=10; food={x:Math.floor(Math.random()*16),y:Math.floor(Math.random()*16)}; } else snake.pop();
          document.getElementById('score').textContent='Score: '+score;
          ctx.fillStyle='#02060a'; ctx.fillRect(0,0,320,320);
          ctx.fillStyle='#fb7185'; ctx.fillRect(food.x*20+2,food.y*20+2,16,16);
          ctx.fillStyle='#10b981'; snake.forEach(function(s){ ctx.fillRect(s.x*20+1,s.y*20+1,18,18); });
        }, 160);
    """.trimIndent()

    private fun gameMinesweeperJs() = """
        var mines=[],rev=[],score=0; for(var i=0;i<36;i++){ mines.push(Math.random()<0.18); rev.push(false); }
        function tap(i){ if(rev[i]) return; rev[i]=true; if(mines[i]){ alert('Boom! Resetting.'); for(var k=0;k<36;k++){mines[k]=Math.random()<0.18;rev[k]=false;} score=0; } else score+=5; render(); }
        function adj(i){ var r=Math.floor(i/6),c=i%6,n=0; for(var dr=-1;dr<=1;dr++)for(var dc=-1;dc<=1;dc++){ var nr=r+dr,nc=c+dc; if(nr>=0&&nr<6&&nc>=0&&nc<6&&mines[nr*6+nc])n++; } return n; }
        function render(){
          document.getElementById('score').textContent='Score: '+score;
          var h='<div style="display:grid;grid-template-columns:repeat(6,1fr);gap:6px">';
          for(var i=0;i<36;i++) h+='<button onclick="tap('+i+')" style="height:46px;border-radius:8px;border:1px solid #1e293b;background:'+(rev[i]?'#0d1d2d':'#111827')+';color:#f8fafc;font-weight:700">'+(rev[i]?(mines[i]?'*':adj(i)):'')+'</button>';
          h+='</div>'; document.getElementById('stage').innerHTML=h;
        }
        render();
    """.trimIndent()

    private fun gameMemoryJs() = """
        var syms=['A','B','C','D','E','F','G','H'], cards=syms.concat(syms).sort(function(){return Math.random()-0.5;}), open=[], matched={}, score=0;
        function flip(i){
          if(matched[i]||open.indexOf(i)!==-1||open.length===2) return;
          open.push(i); render();
          if(open.length===2){
            setTimeout(function(){
              if(cards[open[0]]===cards[open[1]]){ matched[open[0]]=true; matched[open[1]]=true; score+=20; }
              open=[]; render();
            }, 450);
          }
        }
        function render(){
          document.getElementById('score').textContent='Score: '+score;
          var h='<div style="display:grid;grid-template-columns:repeat(4,1fr);gap:8px">';
          cards.forEach(function(s,i){ var show=matched[i]||open.indexOf(i)!==-1; h+='<button onclick="flip('+i+')" style="height:64px;border-radius:10px;border:1px solid #1e293b;background:'+(show?'#072936':'#0f172a')+';color:#f8fafc;font-size:20px;font-weight:700">'+(show?s:'?')+'</button>'; });
          h+='</div>'; document.getElementById('stage').innerHTML=h;
        }
        render();
    """.trimIndent()

    private fun gameTicTacJs() = """
        var b=['','','','','','','','',''], score=0;
        function play(i){
          if(b[i]) return; b[i]='X';
          var free=b.map(function(v,idx){return v?'':idx;}).filter(function(v){return v!=='';});
          if(free.length){ b[free[Math.floor(Math.random()*free.length)]]='O'; score+=10; }
          render();
        }
        function reset(){ b=['','','','','','','','','']; render(); }
        function render(){
          document.getElementById('score').textContent='Moves: '+score;
          var h='<div style="display:grid;grid-template-columns:repeat(3,1fr);gap:8px">';
          b.forEach(function(v,i){ h+='<button onclick="play('+i+')" style="height:82px;border-radius:12px;border:1px solid #1e293b;background:#0d141e;color:#00d4ff;font-size:28px;font-weight:700">'+v+'</button>'; });
          h+='</div><button class="btn" style="width:100%;margin-top:12px" onclick="reset()">Reset Board</button>';
          document.getElementById('stage').innerHTML=h;
        }
        render();
    """.trimIndent()

    private fun gameReactionJs() = """
        var state='idle', t0=0;
        function tap(){
          if(state==='idle'){ state='wait'; render('Wait for green...'); setTimeout(function(){ state='go'; t0=performance.now(); render('TAP NOW!'); }, 1000+Math.random()*2000); }
          else if(state==='go'){ var ms=Math.round(performance.now()-t0); state='idle'; document.getElementById('score').textContent=ms+' ms'; render('Result: '+ms+' ms — Tap to retry'); }
        }
        function render(msg){
          var bg=state==='go'?'#059669':(state==='wait'?'#7f1d1d':'#0f172a');
          document.getElementById('stage').innerHTML='<div onclick="tap()" style="height:240px;border-radius:14px;background:'+bg+';display:flex;align-items:center;justify-content:center;font-size:20px;font-weight:700;text-align:center;padding:16px">'+msg+'</div>';
        }
        render('Tap to test reflex speed');
    """.trimIndent()

    private fun gameArcadeCanvasJs(title: String) = """
        var st=document.getElementById('stage');
        st.innerHTML='<canvas id="c" width="340" height="280" style="width:100%;background:#02060a;border-radius:12px"></canvas><p style="color:#94a3b8;font-size:12px;text-align:center;margin:8px 0 0">Tap anywhere on canvas to control $title</p>';
        var c=document.getElementById('c'), ctx=c.getContext('2d'), px=170, bx=170, by=120, vx=3, vy=3, score=0;
        c.addEventListener('pointermove', function(e){ var r=c.getBoundingClientRect(); px=(e.clientX-r.left)*(340/r.width); });
        c.addEventListener('pointerdown', function(e){ var r=c.getBoundingClientRect(); px=(e.clientX-r.left)*(340/r.width); });
        setInterval(function(){
          bx+=vx; by+=vy;
          if(bx<8||bx>332) vx=-vx;
          if(by<8) vy=-vy;
          if(by>244 && Math.abs(bx-px)<48){ vy=-Math.abs(vy); score+=10; }
          if(by>280){ bx=170; by=80; score=0; }
          document.getElementById('score').textContent='Score: '+score;
          ctx.fillStyle='#02060a'; ctx.fillRect(0,0,340,280);
          ctx.fillStyle='#00d4ff'; ctx.beginPath(); ctx.arc(bx,by,8,0,Math.PI*2); ctx.fill();
          ctx.fillStyle='#10b981'; ctx.fillRect(px-42,252,84,12);
        }, 16);
    """.trimIndent()
}
