(function(){
  function runPatches(){
    try{
      if(typeof window.bindTicketDetailRows==='function') window.bindTicketDetailRows();
      if(typeof window.bindCabinetClicks==='function') window.bindCabinetClicks();
      if(typeof window.bindIptvTicketRows==='function') window.bindIptvTicketRows();
    }catch(e){ console.warn('mobile patch:',e); }
  }
  var oldRender=window.render;
  if(typeof oldRender==='function'){
    window.render=function(d){
      window.lastDashboardData=d;
      var out=oldRender(d);
      setTimeout(runPatches,0);
      return out;
    };
  }
  document.addEventListener('DOMContentLoaded',runPatches);
  setTimeout(runPatches,50);
})();