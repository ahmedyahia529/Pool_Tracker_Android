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

  function ensurePanel(text){
    var p=panelM(text);
    if(p) return p;
    var app=document.getElementById('app');
    if(!app) return null;
    p=document.createElement('section');
    p.className='panel';
    p.innerHTML='<h2>'+escM(text)+'</h2><div class="empty">No data available.</div>';
    app.appendChild(p);
    return p;
  }

  function patchReport(d){
    try{
      d=d||{};
      var t=d.session_totals||{};
      var c=d.current||{};

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
    if(window.ANDROID_REPORT_DATA) setTimeout(function(){patchReport(window.ANDROID_REPORT_DATA)},100);
  }

  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',hook);
  else hook();
  setTimeout(hook,300);
})();
  // IPTV daily-history parity: use session_totals retained tickets (17 secondary / 8 our pool)
  // rather than only current-run arrays, and add the Desktop-style detail/list modals.
  (function(){
    function escI(s){return String(s==null?'':s).replace(/[&<>"']/g,function(m){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m];});}
    function nI(v){return Number(v||0).toLocaleString();}
    function ensureModal(){
      var el=document.getElementById('androidIptvModal');
      if(el)return el;
      el=document.createElement('div');
      el.id='androidIptvModal';
      el.style.cssText='display:none;position:fixed;inset:0;z-index:99999;background:rgba(2,8,20,.84);padding:24px;overflow:auto;backdrop-filter:blur(5px)';
      document.body.appendChild(el);
      return el;
    }
    function cell(label,value){
      return '<div style="background:#131e31;border:1px solid rgba(255,255,255,.08);border-radius:12px;padding:12px 14px">'+
        '<div style="font-size:11px;letter-spacing:.08em;color:#7f9bbd;margin-bottom:6px">'+escI(label)+'</div>'+
        '<div style="font-size:16px;font-weight:700;color:#f1f5ff;word-break:break-word">'+escI(value)+'</div></div>';
    }
    window.closeAndroidIptvModal=function(){
      var el=document.getElementById('androidIptvModal');
      if(el){el.style.display='none';el.innerHTML='';}
    };
    window.openAndroidIptvTicket=function(kind,index){
      var d=window.__ANDROID_IPTV_DATA||{}, list=kind==='our'?d.our:d.second, x=(list||[])[index]||{};
      var ticket=x.ticket_id||'-', title=x.ticket_title||x.category||'IPTV Ticket';
      var href=x.href||'', platform=x.platform||x.product||'-';
      var created=x.created_at||x.transfer_date||'-';
      var firstSeen=x.first_seen_at?('Run #'+(x.first_seen_run||'-')+' · '+x.first_seen_at):'-';
      var lastSeen=x.last_seen_at?('Run #'+(x.last_seen_run||'-')+' · '+x.last_seen_at):'-';
      var el=ensureModal();
      el.innerHTML='<div style="max-width:1080px;margin:2vh auto;background:#0d1729;border:1px solid rgba(104,185,255,.28);border-radius:18px;box-shadow:0 20px 70px rgba(0,0,0,.45);padding:22px">'+
        '<div style="display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:8px"><div>'+
          '<div style="font-size:24px;font-weight:800;color:#f6f8ff">📺 IPTV Ticket · '+escI(ticket)+'</div>'+
          '<div style="font-size:14px;color:#9db4d3;margin-top:6px">'+escI(kind==='our'?'Our Pool':'Secondary IPTV Pool')+' · selected day</div>'+ 
        '</div><button onclick="window.closeAndroidIptvModal()" style="border:1px solid rgba(104,185,255,.4);background:#10243b;color:#9fd9ff;border-radius:10px;padding:10px 14px">Close ✕</button></div>'+
        (href?'<div style="margin:8px 0 14px"><a href="'+escI(href)+'" target="_blank" rel="noopener" style="color:#77ccff;font-weight:700;text-decoration:none">Open Ticket in TTS ↗</a></div>':'')+
        '<div style="display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px">'+
          cell('TICKET ID',ticket)+cell('TICKET TITLE',title)+
          cell('STATUS',x.status||'-')+cell('PLATFORM',platform)+
          cell('ACCOUNT',x.account||'-')+cell('CATEGORY',x.category||'-')+
          cell('DATE CREATED / TRANSFER',created)+cell('DS LAM / CABINET CODE',x.cabinet_code||'-')+
          cell('CABINET NAME',x.cabinet_name||x.cabinet||'-')+cell('CABINET IP',x.cabinet_ip||'-')+
          cell('FIRST SEEN',firstSeen)+cell('LAST SEEN',lastSeen)+
          cell('DAILY OCCURRENCES',x.occurrences!=null?x.occurrences:'-')+
        '</div></div>';
      el.style.display='block';
    };
    window.openAndroidIptvList=function(kind){
      var d=window.__ANDROID_IPTV_DATA||{}, list=kind==='our'?d.our:d.second;
      list=list||[];
      var title=kind==='our'?'📺 IPTV Tickets · Our Pool':'📺 IPTV Tickets Received Today';
      var subtitle=kind==='our'?('Our Pool · '+nI(list.length)+' unique Ticket IDs retained for the selected day.'):('Secondary IPTV Pool · '+nI(list.length)+' unique Ticket IDs retained for the selected day.');
      var rows=list.map(function(x,i){
        return '<div style="display:flex;align-items:center;gap:12px;background:#101c30;border:1px solid rgba(255,255,255,.08);border-radius:12px;padding:14px;margin-bottom:10px">'+
          '<div style="flex:1;min-width:0"><div style="font-size:16px;font-weight:800;color:#f5f8ff">'+escI(x.ticket_id||'-')+' · '+escI(x.ticket_title||x.category||'IPTV')+'</div>'+ 
          '<div style="margin-top:5px;font-size:12px;color:#91a8c6">'+escI(x.platform||x.product||'-')+' · Account '+escI(x.account||'-')+' · '+escI(x.cabinet_name||x.cabinet||x.cabinet_code||'-')+' · '+nI(x.occurrences==null?1:x.occurrences)+' runs</div></div>'+ 
          '<button onclick="window.openAndroidIptvTicket(\''+kind+'\','+i+')" style="flex:none;border:1px solid rgba(255,93,140,.4);background:#151e33;color:#ffd0df;border-radius:999px;padding:8px 13px">View ↗</button></div>';
      }).join('');
      var el=ensureModal();
      el.innerHTML='<div style="max-width:1080px;margin:2vh auto;background:#0d1729;border:1px solid rgba(104,185,255,.28);border-radius:18px;box-shadow:0 20px 70px rgba(0,0,0,.45);padding:22px">'+
        '<div style="display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:10px"><div>'+
          '<div style="font-size:24px;font-weight:800;color:#f6f8ff">'+escI(title)+'</div>'+ 
          '<div style="font-size:14px;color:#9db4d3;margin-top:5px">'+escI(subtitle)+'</div></div>'+ 
          '<button onclick="window.closeAndroidIptvModal()" style="border:1px solid rgba(104,185,255,.4);background:#10243b;color:#9fd9ff;border-radius:10px;padding:10px 14px">Close ✕</button></div>'+ 
        '<div style="max-height:68vh;overflow:auto;padding-right:4px">'+(rows||'<div style="color:#8ea6c5;padding:20px 4px">No IPTV tickets retained for this selected day.</div>')+'</div></div>';
      el.style.display='block';
    };
    window.__androidSetIptvCards=function(d){
      d=d||{}; var t=d.session_totals||{};
      var second=Array.isArray(t.iptv_second_pool_daily_tickets)?t.iptv_second_pool_daily_tickets:[];
      var our=Array.isArray(t.iptv_our_pool_daily_tickets)?t.iptv_our_pool_daily_tickets:[];
      if(!second.length && d.current && Array.isArray(d.current.iptv_second_pool_tickets))second=d.current.iptv_second_pool_tickets;
      if(!our.length && d.current && Array.isArray(d.current.iptv_our_pool_tickets))our=d.current.iptv_our_pool_tickets;
      window.__ANDROID_IPTV_DATA={second:second,our:our};
      var sp=panelM('IPTV · Tickets Received Today');
      if(sp){
        var lastS=second.length?second[second.length-1]:{};
        sp.innerHTML='<div class="panel-title-row"><h2>📺 IPTV · Tickets Received Today</h2><span class="section-badge">SECONDARY POOL</span></div>'+ 
          '<div class="repeat-list"><div class="repeat-item" onclick="window.openAndroidIptvList(\'secondary\')" style="cursor:pointer">'+ 
          '<div class="panel-title-row"><b>📺 '+nI(second.length)+' IPTV tickets received today</b><b style="color:#77ccff">View details ↗</b></div>'+ 
          '<div class="sub">Latest: '+escI(lastS.ticket_id||'-')+' · '+escI(lastS.ticket_title||lastS.category||'-')+'</div></div></div>';
      }
      var op=panelM('IPTV · Our Pool Tickets');
      if(op){
        var lastO=our.length?our[our.length-1]:{};
        op.innerHTML='<div class="panel-title-row"><h2>📺 IPTV · Our Pool Tickets</h2><span class="section-badge">OUR POOL</span></div>'+ 
          '<div class="repeat-list"><div class="repeat-item" onclick="window.openAndroidIptvList(\'our\')" style="cursor:pointer">'+ 
          '<div class="panel-title-row"><b>📺 '+nI(our.length)+' IPTV tickets received in Our Pool today</b><b style="color:#77ccff">View details ↗</b></div>'+ 
          '<div class="sub">Latest: '+escI(lastO.ticket_id||'-')+' · '+escI(lastO.ticket_title||lastO.category||'-')+'</div></div></div>';
      }
    };
    var r=window.render;
    if(typeof r==='function'&&!r.__androidIptvDaily){
      function w(d){r(d);setTimeout(function(){window.__androidSetIptvCards(d)},70);}
      w.__androidIptvDaily=true; window.render=w;
    }
    if(window.ANDROID_REPORT_DATA)setTimeout(function(){window.__androidSetIptvCards(window.ANDROID_REPORT_DATA)},150);
  })();
