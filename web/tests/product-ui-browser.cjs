// Built-app visual/interaction checks. No external requests or real accounts.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict')
const puppeteer=require('puppeteer-core')
const dist=path.resolve(__dirname,'../dist'),output=path.resolve(__dirname,'../../.gradle/product-ui')
const mime={'.js':'application/javascript','.css':'text/css','.html':'text/html','.svg':'image/svg+xml','.png':'image/png','.webmanifest':'application/manifest+json'}
const server=http.createServer((request,response)=>{
  const pathname=new URL(request.url,'http://localhost').pathname
  const file=path.resolve(dist,['/','/app','/verify'].includes(pathname)?'index.html':pathname.slice(1))
  if(!file.startsWith(dist+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){response.writeHead(404).end();return}
  response.writeHead(200,{'Content-Type':mime[path.extname(file)]||'application/octet-stream'});response.end(fs.readFileSync(file))
})
async function captureLanding(page,name,width,height){
  await page.setViewport({width,height})
  await page.evaluate(async()=>{scrollTo(0,0);await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))})
  const bottom=await page.$eval('.home-footer',element=>Math.ceil(element.getBoundingClientRect().bottom+scrollY))
  assert.ok(await page.evaluate(bottom=>document.documentElement.scrollHeight-bottom<70,bottom),'landing footer should end the page after resizing')
  await page.screenshot({path:path.join(output,name),clip:{x:0,y:0,width,height:bottom},captureBeyondViewport:true})
}
async function main(){
  const executablePath=process.argv[2]||process.env.BROWSER_EXECUTABLE
  assert.ok(executablePath,'Supply a Chromium executable')
  fs.mkdirSync(output,{recursive:true})
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve))
  const origin=`http://127.0.0.1:${server.address().port}`
  const browser=await puppeteer.launch({executablePath,headless:'new',args:['--no-proxy-server']})
  const errors=[]
  try{
    for(const locale of ['zh-CN','en','ja'])for(const theme of ['light','dark']){
      const context=await browser.createIncognitoBrowserContext(),page=await context.newPage()
      page.on('pageerror',error=>errors.push(error.message))
      await page.setBypassServiceWorker(true)
      await page.evaluateOnNewDocument(({locale,theme})=>{localStorage.setItem('locale',locale);localStorage.setItem('theme',theme)},{locale,theme})
      await page.setRequestInterception(true)
      page.on('request',request=>{
        const url=new URL(request.url())
        if(url.origin===origin){request.continue();return}
        if(url.pathname==='/api/auth/verify-code'){
          request.respond({status:200,contentType:'application/json',headers:{'Access-Control-Allow-Origin':'*','Access-Control-Allow-Headers':'*','Access-Control-Allow-Methods':'*'},body:JSON.stringify({code:0,data:true})});return
        }
        request.abort()
      })
      await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}])
      await page.setViewport({width:1280,height:960})
      await page.goto(origin);await page.waitForSelector('.home-hero h1')
      await page.waitForSelector('#moment-panel .daily-preview')
      assert.equal(await page.evaluate(()=>document.body.textContent.includes('✳')||document.body.textContent.includes('✧')),false,'brand pages do not use generic star ornaments')
      for(const width of [1280,390,320]){
        await page.setViewport({width,height:900})
        assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true,`${locale}/${theme}/${width} landing overflow`)
        assert.equal(await page.$$eval('.home-cta>*',items=>items.length),2)
        assert.equal(await page.$eval('.home-cta>*',el=>new URL(el.href).pathname),'/app')
        assert.ok(await page.evaluate(()=>document.documentElement.scrollHeight-(document.querySelector('.home-footer').getBoundingClientRect().bottom+scrollY)<70),`${locale}/${theme}/${width} excessive footer space`)
      }
      await page.click('#moment-discover');await page.waitForSelector('#moment-discover[aria-selected="true"]')
      await page.keyboard.press('ArrowDown');await page.waitForSelector('#moment-assistant[aria-selected="true"]')
      await page.keyboard.press('Home');await page.waitForSelector('#moment-friends[aria-selected="true"]')
      await page.waitForSelector('#moment-panel .conversation-preview')
      assert.ok(await page.$eval('.preview-row',el=>parseFloat(getComputedStyle(el).animationDuration)<.01),'reduced motion respected')
      await page.click('#moment-discover');await page.waitForSelector('#moment-panel .daily-preview')
      assert.equal(await page.$$eval('.daily-preview article',items=>items.length),2,'daily question is shown as answers rather than another private chat')
      if(locale==='zh-CN'){
        await page.evaluate(()=>document.activeElement?.blur())
        await captureLanding(page,`landing-${theme}.png`,1280,960)
        await captureLanding(page,`landing-mobile-${theme}.png`,390,844)
      }
      await page.goto(origin+'/app');await page.waitForSelector('#login-account')
      assert.equal(await page.$eval('#login-password',el=>el.autocomplete),'current-password')
      assert.equal(await page.$eval('label[for="login-account"]',el=>el.control.id),'login-account')
      await page.click('.toggle-mode');assert.equal(await page.$eval('#login-password',el=>el.autocomplete),'new-password')
      assert.equal(await page.$eval('#login-account',el=>el.type),'email')
      await page.click('.toggle-mode')
      for(const width of [1280,390,320]){
        await page.setViewport({width,height:844})
        assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true,`${locale}/${theme}/${width} login overflow`)
        const bounds=await page.$eval('#login-password',el=>{const r=el.getBoundingClientRect();return {left:r.left,right:r.right}})
        assert.ok(bounds.left>=0&&bounds.right<=width,`${locale}/${theme}/${width} password bounds`)
      }
      if(locale==='zh-CN'){
        await page.setViewport({width:1280,height:960});await page.screenshot({path:path.join(output,`login-${theme}.png`),fullPage:true})
        await page.setViewport({width:390,height:844});await page.screenshot({path:path.join(output,`login-mobile-${theme}.png`),fullPage:true})
      }
      await page.click('.login-back');await page.waitForSelector('.home-hero h1')
      await page.click('.footer-actions button');await page.waitForSelector('.reward-box[role="dialog"]')
      await page.keyboard.press('Escape');await page.waitForSelector('.reward-box',{hidden:true})
      await page.goto(origin+'/app');await page.waitForSelector('#login-account')
      await page.click('.toggle-mode');await page.waitForSelector('#login-account[type="email"]')
      await page.type('#login-account','fixture@example.invalid');await page.type('#login-password','FixturePass123!')
      await page.click('.panel .primary');await page.waitForSelector('input[autocomplete="one-time-code"]')
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true,'verification fits narrow viewport')
      const fieldColors=await page.$eval('input[autocomplete="one-time-code"]',el=>({field:getComputedStyle(el).backgroundColor,panel:getComputedStyle(el.closest('.panel')).backgroundColor}))
      assert.equal(fieldColors.field,fieldColors.panel,'verification uses the same paper surface in both themes')
      if(locale==='zh-CN')await page.screenshot({path:path.join(output,`verify-mobile-${theme}.png`),fullPage:true})
      await page.click('.verify-brand');await page.waitForSelector('.home-hero h1')
      await context.close()
    }
    assert.deepEqual(errors,[])
    console.log('PASS: landing/login in three languages, light/dark, 1280/390/320px; scene keyboard navigation, reduced motion, CTA routes, registration/verification, reward dialog Escape, return home. Screenshots: '+output)
  }finally{await browser.close();await new Promise(resolve=>server.close(resolve))}
}
main().catch(error=>{console.error(error);server.close();process.exitCode=1})
