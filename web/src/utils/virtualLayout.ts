/** Prefix sums are rebuilt only when rows/heights change; scrolling uses binary search. */
export function virtualLayout(keys: readonly string[], heights: ReadonlyMap<string, number>, estimate = 100) {
  const offsets = [0]
  for (const key of keys) offsets.push(offsets[offsets.length - 1] + (heights.get(key) ?? estimate))
  const total = offsets[offsets.length - 1]
  function indexAt(position: number) {
    let low = 0, high = keys.length
    while (low < high) {
      const middle = (low + high) >>> 1
      if (offsets[middle + 1] <= position) low = middle + 1
      else high = middle
    }
    return Math.min(low, Math.max(0, keys.length - 1))
  }
  return { offsets, total, indexAt,
    range(top: number, height: number, overscan = 500) {
      return { start: indexAt(Math.max(0, top - overscan)), end: Math.min(keys.length, indexAt(top + height + overscan) + 1) }
    }
  }
}

/** Fenwick height index: one row measurement changes O(log n) nodes, not all offsets. */
export class IncrementalVirtualLayout {
  private tree:number[]=[0]
  private sizes:number[]=[]
  private indices=new Map<string,number>()
  constructor(private readonly estimate=100){}
  reset(keys:readonly string[],heights:ReadonlyMap<string,number>) {
    this.indices.clear();this.sizes=keys.map((key,index)=>{this.indices.set(key,index);return heights.get(key)??this.estimate})
    this.tree=[0,...this.sizes]
    for(let i=1;i<this.tree.length;i++){const parent=i+(i&-i);if(parent<this.tree.length)this.tree[parent]+=this.tree[i]}
  }
  offsetAt(end:number){let sum=0;for(let i=Math.min(Math.max(0,end),this.sizes.length);i>0;i-=i&-i)sum+=this.tree[i];return sum}
  get total(){return this.offsetAt(this.sizes.length)}
  update(key:string,height:number){
    const index=this.indices.get(key)
    if(index===undefined||!Number.isFinite(height)||height<=0)return
    const delta=height-this.sizes[index];this.sizes[index]=height
    for(let i=index+1;i<this.tree.length;i+=i&-i)this.tree[i]+=delta
  }
  indexAt(position:number){
    let index=0,sum=0,step=1
    while(step*2<=this.sizes.length)step*=2
    for(;step>0;step>>=1){const next=index+step;if(next<this.tree.length&&sum+this.tree[next]<=position){index=next;sum+=this.tree[next]}}
    return Math.min(index,Math.max(0,this.sizes.length-1))
  }
  range(top:number,height:number,overscan=500){return {start:this.indexAt(Math.max(0,top-overscan)),end:Math.min(this.sizes.length,this.indexAt(top+height+overscan)+1)}}
}
