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
