(() => {
  'use strict';
  const demos = window.GESTURE_DEMOS;
  const $ = id => document.getElementById(id);
  const motionPreference = matchMedia('(prefers-reduced-motion: reduce)');
  const state = {kind:'signal', category:'all', area:'full', query:'', selected:'touch_top_right_down', playing:!motionPreference.matches, time:0, speed:1};
  const themes = [['紫色','#604985','#d5c7f2','#f5f1fb'],['蓝色','#205f82','#a9ddf7','#eff8fd'],['粉色','#84445d','#f6c1d5','#fdf3f7'],['绿色','#2a6859','#b9e9da','#eff9f5'],['黑色','#3e4653','#dee3ea','#f3f5f7']];
  let previous = performance.now();
  const selected = () => demos.find(d => d.id === state.selected);

  themes.forEach(([name,accent,soft,tint],i) => {
    const button=document.createElement('button');button.className='theme-dot';button.style.setProperty('--color',soft);
    button.title=name;button.setAttribute('aria-label',name+'皮肤');button.setAttribute('aria-pressed',i===0?'true':'false');
    button.addEventListener('click',()=>{
      document.documentElement.style.setProperty('--accent',accent);document.documentElement.style.setProperty('--soft',soft);document.documentElement.style.setProperty('--tint',tint);
      $('themes').querySelectorAll('button').forEach(b=>b.setAttribute('aria-pressed',String(b===button)));
    });$('themes').append(button);
  });

  function renderFilters(){
    const choices=state.kind==='signal'?[['all','全部'],['basic','普通触摸'],['edge','边缘手势']]:[['tap','点按'],['swipe','滑动'],['circle','画圈']];
    $('filters').replaceChildren();
    choices.forEach(([id,label])=>{
      const button=document.createElement('button');button.textContent=label;button.setAttribute('aria-pressed',String(state.category===id));
      button.addEventListener('click',()=>{state.category=id;renderFilters();const first=visibleDemos()[0];if(first)choose(first);else renderList();});$('filters').append(button);
    });
    $('area-field').hidden=state.kind!=='action'||state.category!=='tap';
  }

  function visibleDemos(){return demos.filter(d=>d.kind===state.kind&&(state.category==='all'||d.category===state.category)
    &&(state.kind!=='action'||d.category!=='tap'||d.area===state.area)&&d.name.includes(state.query));}

  function renderList(){
    const list=visibleDemos(),scroll=$('catalog-list').scrollTop;$('result-count').textContent=list.length+' 项';$('catalog-list').replaceChildren();
    if(!list.length){const empty=document.createElement('p');empty.className='empty';empty.textContent='没有匹配的动作，请换个关键词。';$('catalog-list').append(empty);return;}
    list.forEach(d=>{
      const button=document.createElement('button');button.className='demo-card';button.dataset.id=d.id;
      button.setAttribute('aria-pressed',String(d.id===state.selected));
      button.innerHTML=d.thumbnail;
      const copy=document.createElement('span'),title=document.createElement('strong'),meta=document.createElement('small');
      title.textContent=d.name;meta.textContent=d.kind==='signal'?'受控端 · '+(d.category==='edge'?'边缘轨迹':'屏幕触摸')
        :d.fingers+' 根手指 · '+(d.category==='tap'?d.tapCount+' 次轻点':d.category==='circle'?'连续画圈':'同步移动');
      copy.append(title,meta);button.append(copy);
      const arrow=document.createElement('span');arrow.className='arrow';arrow.textContent='›';arrow.setAttribute('aria-hidden','true');button.append(arrow);
      let hold=null,longPressed=false;
      const clear=()=>{clearTimeout(hold);hold=null;};
      button.addEventListener('pointerdown',event=>{if(event.button!==0)return;longPressed=false;hold=setTimeout(()=>{longPressed=true;choose(d,false);expand();},550);});
      ['pointerup','pointercancel','pointerleave'].forEach(event=>button.addEventListener(event,clear));
      button.addEventListener('click',()=>{clear();if(longPressed){longPressed=false;return;}choose(d,matchMedia('(max-width:680px)').matches);});
      button.addEventListener('contextmenu',event=>{event.preventDefault();clear();choose(d,false);expand();});
      $('catalog-list').append(button);
    });
    $('catalog-list').scrollTop=scroll;
  }

  function prepareSvg(host,svg){host.innerHTML=svg;const root=host.querySelector('svg');root.pauseAnimations();root.setCurrentTime(state.time);}
  function choose(d,scroll=false){
    state.selected=d.id;state.time=0;state.playing=!motionPreference.matches;previous=performance.now();
    $('demo-name').textContent=d.name;$('description').textContent=d.description;$('detail').textContent=d.detail;
    $('demo-role').textContent=d.kind==='signal'?'受控端收到的触控轨迹':'用户在本机的触发方式';
    $('download').href=d.file;$('download').download=d.file.split('/').pop();
    prepareSvg($('stage'),d.svg);renderList();syncControls();
    const card=Array.from($('catalog-list').children).find(c=>c.dataset.id===d.id);
    if(card){const list=$('catalog-list'),r=card.getBoundingClientRect(),bounds=list.getBoundingClientRect();
      if(r.top<bounds.top)list.scrollTop-=bounds.top-r.top;else if(r.bottom>bounds.bottom)list.scrollTop+=r.bottom-bounds.bottom;}
    if(scroll)document.querySelector('.viewer').scrollIntoView({behavior:motionPreference.matches?'auto':'smooth',block:'start'});
  }
  function syncControls(){
    $('play').textContent=state.playing?'暂停':'播放';$('play').setAttribute('aria-label',state.playing?'暂停动画':'播放动画');
    $('modal-play').textContent=state.playing?'暂停':'播放';
    $('timeline').value=state.time;$('time').textContent=state.time.toFixed(1)+' / '+selected().cycle.toFixed(1)+' 秒';
  }
  function draw(){
    document.querySelectorAll('#stage > svg,#modal-stage > svg').forEach(svg=>svg.setCurrentTime(state.time));
    $('timeline').value=state.time;$('time').textContent=state.time.toFixed(1)+' / '+selected().cycle.toFixed(1)+' 秒';
  }
  function play(){state.playing=!state.playing;previous=performance.now();syncControls();}
  function replay(){state.time=0;state.playing=true;previous=performance.now();draw();syncControls();}
  function expand(){
    const d=selected();$('modal-title').textContent=d.name;
    const prefix=d.id.replaceAll(':','-').replaceAll('_','-');
    prepareSvg($('modal-stage'),d.svg.replaceAll(prefix,prefix+'-large'));
    if(!$('enlarged').open)$('enlarged').showModal();syncControls();
  }
  document.querySelectorAll('[data-kind]').forEach(button=>button.addEventListener('click',()=>{
    state.kind=button.dataset.kind;state.category=state.kind==='signal'?'all':'tap';state.query='';$('search').value='';
    document.querySelectorAll('[data-kind]').forEach(b=>b.setAttribute('aria-selected',String(b===button)));
    $('catalog-title').textContent=state.kind==='signal'?'受控端收到的触摸':'用户怎样触发动作';
    $('catalog-note').textContent=state.kind==='signal'?'演示受控屏幕上的触点，不代表某款设备绑定的具体功能。':'点按含 5 种区域；数字表示同一组手指。双指、三指动作不会直接发送多指触控。';
    renderFilters();choose(visibleDemos()[0]);
  }));
  $('area').addEventListener('change',()=>{const old=selected();state.area=$('area').value;const list=visibleDemos();
    const match=list.find(d=>d.fingers===old.fingers&&d.tapCount===old.tapCount)||list[0];if(match)choose(match);else renderList();});
  $('search').addEventListener('input',()=>{state.query=$('search').value.trim();renderList();});
  $('play').addEventListener('click',play);$('replay').addEventListener('click',replay);
  $('speed').addEventListener('change',()=>{state.speed=Number($('speed').value);previous=performance.now();});
  $('timeline').addEventListener('input',()=>{state.time=Number($('timeline').value);state.playing=false;draw();syncControls();});
  $('expand').addEventListener('click',expand);$('close-modal').addEventListener('click',()=>$('enlarged').close());
  $('enlarged').addEventListener('close',()=>$('modal-stage').replaceChildren());
  $('enlarged').addEventListener('click',event=>{if(event.target===$('enlarged')){const b=$('enlarged').getBoundingClientRect();if(event.clientX<b.left||event.clientX>b.right||event.clientY<b.top||event.clientY>b.bottom)$('enlarged').close();}});
  $('modal-play').addEventListener('click',play);$('modal-replay').addEventListener('click',replay);
  document.addEventListener('visibilitychange',()=>{previous=performance.now();});
  function frame(now){if(state.playing&&!document.hidden){state.time=(state.time+(now-previous)/1000*state.speed)%selected().cycle;draw();}previous=now;requestAnimationFrame(frame);}
  renderFilters();choose(selected());requestAnimationFrame(frame);
})();
