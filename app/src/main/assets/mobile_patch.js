(function(){
  'use strict';

  function escM(s){
    return String(s==null?'':s).replace(/[&<>"']/g,function(m){
      return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m];
    });
  }
  function nM(v){ return Number(v||0).toLocaleString(); }

  function panelM(text){
    var ps=document.querySelectorAll('.panel');
    for(var i=0;i<ps.length;i++){
      var h=ps[i].querySelector('h2');
      if(h && h.textContent.indexOf(text)>=0) return ps[i];
    }
    return null;
  }

  function ensurePanel(text,top){
    var p=panelM(text);
    if(p) return p;
    var app=document.getElementById('app');
    if(!app) return null;
    p=document.createElement('section');
    p.className='panel';
    p.innerHTML='<h2>'+escM(text)+'</h2><div class="empty">No data available.</div>';
    if(top && app.firstChild) app.insertBefore(p,app.firstChild);
    else app.appendChild(p);
    return p;
  }

  function injectAlertStyle(){
    if(document.getElementById('sentinel-alert-style')) return;
    var s=document.createElement('style');
    s.id='sentinel-alert-style';
    s.textContent=
      '.sentinel-alert-head{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:12px}'+
      '.sentinel-alert-count{min-width:28px;height:28px;padding:0 9px;border-radius:99px;display:inline-grid;place-items:center;background:#ff667822;border:1px solid #ff667866;color:#ff9aaa;font-weight:900}'+
      '.sentinel-alert-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}'+
      '.sentinel-alert{display:flex;gap:10px;align-items:flex-start;padding:11px 12px;border:1px solid #ffffff12;border-radius:12px;background:#0d1728}'+
      '.sentinel-alert.critical{border-color:#ff667855;background:linear-gradient(145deg,#24131b,#111b2e)}'+
      '.sentinel-alert.warn{border-color:#f6c45344}'+
      '.sentinel-alert.info{border-color:#55c2ff44}'+
      '.sentinel-alert-icon{width:30px;height:30px;flex:0 0 30px;display:grid;place-items:center;border-radius:9px;background:#ffffff08}'+
      '.sentinel-alert-main{min-width:0;flex:1}.sentinel-alert-main b{display:block}.sentinel-alert-main span{display:block;color:#8ea0b8;font-size:10px;line-height:1.45;margin-top:3px}'+
      '.sentinel-alert-pill{font-size:9px;font-weight:900;padding:3px 6px;border-radius:99px;color:#8ea0b8;border:1px solid #ffffff1a;white-space:nowrap}'+
      '@media(max-width:600px){.sentinel-alert-grid{grid-template-columns:1fr}}';
    s.textContent += '.panel,.card,.sentinel-alert{animation:sentinelIn .42s cubic-bezier(.2,.8,.2,1) both}.panel:nth-of-type(2n){animation-delay:.04s}.panel:nth-of-type(3n){animation-delay:.08s}.sentinel-alert:nth-child(2){animation-delay:.04s}.sentinel-alert:nth-child(3){animation-delay:.08s}.card:hover{transform:translateY(-2px);box-shadow:0 14px 34px #0005}.panel:hover{box-shadow:0 14px 36px #0004}.wrap:before{content:"";position:fixed;inset:0;pointer-events:none;background:radial-gradient(circle at 15% 10%,#55c2ff10,transparent 32%),radial-gradient(circle at 85% 80%,#a78bfa0d,transparent 30%);animation:sentinelPulse 7s ease-in-out infinite alternate}@keyframes sentinelIn{from{opacity:0;transform:translateY(12px) scale(.985)}to{opacity:1;transform:none}}@keyframes sentinelPulse{from{opacity:.45}to{opacity:1}}.sentinel-alert.critical{animation:sentinelIn .42s both, sentinelGlow 2.8s ease-in-out infinite alternate}@keyframes sentinelGlow{from{box-shadow:0 0 0 #ff667800}to{box-shadow:0 0 24px #ff66780d}}';document.head.appendChild(s);
  }

  function buildAlerts(d){
    d=d||{};
    var t=d.session_totals||{}, c=d.current||{}, day=d.session_date||'', stamp=d.last_updated||'';
    var out=[];
    var newTickets=Number(c.new_ticket_count||t.new_tickets_this_run||0);
    if(newTickets>0) out.push({key:'ticket_'+day+'_'+stamp+'_'+newTickets,icon:'🎫',level:'warn',title:'New Tickets Detected',body:newTickets+' new ticket(s) in the latest TTS run.'});

    var near=Number(c.near_violation||0);
    if(near>0) out.push({key:'sla_'+day+'_'+stamp+'_'+near,icon:'⏱️',level:'critical',title:'SLA Alert',body:near+' ticket(s) reached the 1h30m near-violation threshold.'});

    var high=t.high_group_tickets||c.high_group_tickets||[];
    if(high.length){
      var top=high[0]||{};
      out.push({key:'high_'+day+'_'+stamp+'_'+high.length,icon:'🔥',level:'critical',title:'High GroupCount Activity',body:high.length+' ticket(s) reached GroupCount ≥ 5'+(top.ticket_id?' · Highest: '+top.ticket_id+' ('+Number(top.group_count||0)+')':'')+'.'});
    }

    var repeats=t.pool_repeated_cabinet_history||c.pool_repeated_cabinet_history||[];
    if(repeats.length){
      var last=repeats[repeats.length-1]||{}, rc=Number(last.count||0);
      if(rc>=3) out.push({key:'repeat_'+day+'_'+stamp+'_'+(last.cabinet||'')+'_'+rc,icon:'🏢',level:'warn',title:'Repeated Cabinet in Pool',body:(last.cabinet||'A cabinet')+' appeared '+rc+' times in the TTS pool.'});
    }

    var intel=t.cabinet_intelligence||[];
    intel.slice(0,5).forEach(function(x){
      var tickets=Number(x.tickets||0),events=Number(x.escalations||0),risk=String(x.risk_level||'').toUpperCase();
      if(tickets>=5||events>=5||risk==='HIGH'||risk==='CRITICAL'){
        out.push({key:'cab_'+day+'_'+stamp+'_'+(x.cabinet||'')+'_'+tickets+'_'+events,icon:'🚨',level:'critical',title:'Cabinet Activity Spike',body:(x.cabinet||'-')+' · '+tickets+' ticket(s) · '+events+' event(s).'});
      }
    });

    var repeatedCurrent=Number(c.repeated_cabinets||0);
    if(repeatedCurrent>0) out.push({key:'current_repeat_'+day+'_'+stamp+'_'+repeatedCurrent,icon:'🔁',level:'warn',title:'Repeated Cabinets',body:repeatedCurrent+' cabinet(s) are currently repeated in the pool.'});

    var iptv=Number(c.iptv_our_pool_count||t.iptv_our_pool_count||0);
    if(iptv>0) out.push({key:'iptv_'+day+'_'+stamp+'_'+iptv,icon:'📺',level:'info',title:'IPTV Our Pool',body:iptv+' IPTV ticket(s) are currently in Our Pool.'});

    return out;
  }

  function renderAlertCenter(d){
    injectAlertStyle();
    var p=ensurePanel('Sentinel Alerts',true);
    if(!p) return;
    var alerts=buildAlerts(d);
    var critical=alerts.filter(function(x){return x.level==='critical'}).length;
    p.innerHTML='<div class="sentinel-alert-head"><div><h2>🔔 Sentinel Alerts</h2><div class="sub">Live operational events detected from the selected report.</div></div><span class="sentinel-alert-count">'+nM(alerts.length)+'</span></div>'+
      (alerts.length?'<div class="sentinel-alert-grid">'+alerts.map(function(a){
        return '<div class="sentinel-alert '+escM(a.level)+'"><div class="sentinel-alert-icon">'+a.icon+'</div><div class="sentinel-alert-main"><b>'+escM(a.title)+'</b><span>'+escM(a.body)+'</span></div><span class="sentinel-alert-pill">'+escM(a.level==='critical'?'CRITICAL':a.level==='warn'?'ATTENTION':'INFO')+'</span></div>';
      }).join('')+'</div>':'<div class="empty">🟢 No active alerts. Sentinel is quiet.</div>');
  }

  function patchReport(d){
    try{
      d=d||{};
      var t=d.session_totals||{};
      var c=d.current||{};

      renderAlertCenter(d);

      var p=ensurePanel('Cabinet Intelligence');
      if(p){
        var list=t.cabinet_intelligence||[];
        p.innerHTML='<div class="panel-title-row"><h2>🧠 Cabinet Intelligence</h2><span class="section-badge">TOP 10</span></div>'+
          '<div class="sub" style="margin-bottom:10px">Click a cabinet to inspect Tickets, Escalations, Customers and categories.</div>'+
          '<div class="repeat-list">'+
          (list.length?list.slice(0,10).map(function(x,i){
            return '<div class="repeat-item"><div class="panel-title-row"><b>#'+(i+1)+' '+escM(x.cabinet||'-')+'</b><b>'+nM(x.escalations)+' events</b></div>'+
              '<div class="sub">'+nM(x.tickets)+' tickets · '+nM(x.customers)+' customers · '+nM(x.repeat_escalations)+' repeat escalations · '+nM(x.high_group_events)+' high GroupCount events</div>'+
              '<div class="sub">Risk: '+escM(x.risk_level||'-')+' · Score '+nM(x.risk_score)+' · '+escM((x.category_names||[]).join(' · '))+'</div></div>';
          }).join(''):'<div class="empty">No cabinet intelligence available.</div>')+
          '</div>';
      }

      var p2=ensurePanel('Top Repeated Customers');
      if(p2){
        var cust=t.top_repeated_customers||[];
        p2.innerHTML='<div class="panel-title-row"><h2>👤 Top Repeated Customers</h2><span class="section-badge">TOP 10</span></div>'+
          '<div class="sub" style="margin-bottom:10px">Accounts with more than one unique Ticket ID.</div>'+
          '<div class="repeat-list">'+
          (cust.length?cust.slice(0,10).map(function(x){
            return '<div class="repeat-item"><div class="panel-title-row"><b>'+escM(x.account||'-')+'</b><b>'+nM(x.escalations)+' events</b></div><div class="sub">'+nM(x.tickets)+' unique ticket(s)</div></div>';
          }).join(''):'<div class="empty">No repeated customers detected yet.</div>')+
          '</div>';
      }

      var p3=ensurePanel('Peak Escalation Windows');
      if(p3){
        var pc=t.peak_category_windows||{};
        var ps=t.peak_service_windows||{};
        var po=t.peak_escalation_window||{};
        function peakCards(obj){
          return Object.keys(obj).map(function(k){
            var x=obj[k]||{};
            var h=x.hour!=null?String(x.hour).padStart(2,'0')+':00–'+String(x.hour).padStart(2,'0')+':59':'-';
            return '<div class="category-total"><div class="ct-label">'+escM(k)+'</div><div class="ct-value">🔥 '+escM(h)+'</div><div class="sub">'+nM(x.total)+' escalation events</div></div>';
          }).join('');
        }
        var overall=po.hour!=null?String(po.hour).padStart(2,'0')+':00–'+String(po.hour).padStart(2,'0')+':59':'-';
        p3.innerHTML='<div class="panel-title-row"><h2>🔥 Peak Escalation Windows</h2><span class="section-badge">DAILY</span></div>'+
          '<div style="padding:12px 14px;margin-bottom:12px;border:1px solid rgba(255,255,255,.08);border-radius:12px"><b>🔥 Overall Peak</b> · '+escM(overall)+' · <b>'+nM(po.total)+' events</b></div>'+
          '<div class="sub">CATEGORIES</div><div class="category-totals">'+(peakCards(pc)||'<div class="empty">No category peak data.</div>')+'</div>'+
          '<div class="sub" style="margin-top:12px">SERVICE FAMILIES</div><div class="category-totals">'+(peakCards(ps)||'<div class="empty">No service peak data.</div>')+'</div>';
      }

      function ticketPanel(title,rows,high){
        var p=ensurePanel(title);
        if(!p) return;
        rows=rows||[];
        var headers=high?
          '<th>Ticket</th><th>Account</th><th>Product</th><th>Category</th><th>Cabinet</th><th>GroupCount</th><th>Transfer Time</th><th>First Run</th><th>Last Run</th>':
          '<th>Ticket</th><th>Account</th><th>Product</th><th>Category</th><th>Cabinet</th><th>TransferDate</th><th>GroupCount</th>';
        var body=rows.length?rows.map(function(x){
          if(high){
            return '<tr><td>'+escM(x.ticket_id)+'</td><td>'+escM(x.account)+'</td><td>'+escM(x.product)+'</td><td>'+escM(x.category)+'</td><td>'+escM(x.cabinet)+'</td><td>'+nM(x.group_count)+'</td><td>'+escM(x.transfer_time)+'</td><td>'+escM(x.first_seen_run)+'</td><td>'+escM(x.last_seen_run)+'</td></tr>';
          }
          return '<tr><td>'+escM(x.ticket_id)+'</td><td>'+escM(x.account)+'</td><td>'+escM(x.product)+'</td><td>'+escM(x.category)+'</td><td>'+escM(x.cabinet)+'</td><td>'+escM(x.transfer_date)+'</td><td>'+nM(x.group_count)+'</td></tr>';
        }).join(''):'<tr><td colspan="9" class="empty">No matching tickets.</td></tr>';
        p.innerHTML='<div class="panel-title-row"><h2>'+escM(title)+(high?' <span class="section-badge">DAILY</span>':'')+'</h2></div>'+
          '<div class="table-scroll"><table class="table"><thead><tr>'+headers+'</tr></thead><tbody>'+body+'</tbody></table></div>';
      }

      var tickets=(c.tickets||[]).slice().sort(function(a,b){return Number(b.group_count||0)-Number(a.group_count||0)}).slice(0,25);
      ticketPanel('Current Ticket Detail',tickets,false);
      var high=(t.high_group_tickets||[]).slice().sort(function(a,b){return Number(b.group_count||0)-Number(a.group_count||0)});
      ticketPanel('High Group Tickets',high,true);

      setTimeout(function(){
        if(typeof window.bindChartHover==='function') window.bindChartHover();
      },0);
    }catch(e){
      console.log('mobile report patch error',e);
    }
  }

  function hook(){
    var oldRender=window.render;
    if(typeof oldRender==='function' && !oldRender.__androidPatched){
      function wrappedRender(d){
        oldRender(d);
        setTimeout(function(){patchReport(d)},50);
      }
      wrappedRender.__androidPatched=true;
      window.render=wrappedRender;
    }
    if(window.ANDROID_REPORT_DATA) setTimeout(function(){patchReport(window.ANDROID_REPORT_DATA); if(window.ANDROID_FOCUS_ALERT){var ps=document.querySelectorAll('.panel'); for(var i=0;i<ps.length;i++){if((ps[i].textContent||'').indexOf(window.ANDROID_FOCUS_ALERT.replace(/^\S+\s/,''))>=0){ps[i].scrollIntoView({behavior:'smooth',block:'center'});ps[i].style.outline='2px solid #55c2ff88';setTimeout(function(){ps[i].style.outline=''},1800);break;}}}},100);
  }

  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',hook);
  else hook();
  setTimeout(hook,300);
})();
