package com.generalsx.zerohour;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Waru Edition PC keyboard overlay. Every key has an independent persistent
 * enable switch; disabled keys are removed from layout, so the board contracts.
 * Key events go through SDL's normal event queue.
 */
final class WaruGameKeyboardOverlay extends View {
    private static final String PREFS = "waru_game_keyboard";
    private static final int RED=0, BLUE=1, GOLD=2, SILVER=3;
    private static final int PAGE_SIZE=24;
    private static final String[] THEMES={"RED","BLUE","GOLD","SILVER"};

    private static final class Key {
        final String label, pref;
        final int scan, row;
        final float weight;
        Key(String label,int scan,int row,float weight) {
            this.label=label; this.scan=scan; this.row=row; this.weight=weight;
            this.pref="key_scan_"+scan;
        }
    }
    private final ArrayList<Key> keys=new ArrayList<>();
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SharedPreferences prefs;
    private final Map<Integer,Key> pressed=new HashMap<>();
    private final Set<String> disabled=new HashSet<>();
    private float anchorX,anchorY,scale,opacity;
    private int theme,settingsPage=0;
    private boolean enabled,compact,settings;
    private int dragPointer=-1;
    private float dragDx,dragDy;

    WaruGameKeyboardOverlay(Context context) {
        super(context);
        prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        enabled=prefs.getBoolean("enabled",true);
        compact=prefs.getBoolean("collapsed",false);
        theme=clamp(prefs.getInt("theme",BLUE),0,3);
        scale=clamp(prefs.getFloat("scale",.78f),.48f,1.25f);
        opacity=clamp(prefs.getFloat("opacity",.68f),.20f,.95f);
        anchorX=prefs.getFloat("x",.025f);
        anchorY=prefs.getFloat("y",.42f);
        loadKeys();
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        setClickable(true);
    }
    private void loadKeys() {
        // PC function row
        add("ESC",41,0,1); add("F1",58,0,1); add("F2",59,0,1); add("F3",60,0,1);
        add("F4",61,0,1); add("F5",62,0,1); add("F6",63,0,1); add("F7",64,0,1);
        add("F8",65,0,1); add("F9",66,0,1); add("F10",67,0,1); add("F11",68,0,1);
        add("F12",69,0,1); add("PRTSC",70,0,1); add("SCRLK",71,0,1); add("PAUSE",72,0,1);
        // Number row and editing keys
        add("`",53,1,1); add("1",30,1,1); add("2",31,1,1); add("3",32,1,1); add("4",33,1,1);
        add("5",34,1,1); add("6",35,1,1); add("7",36,1,1); add("8",37,1,1); add("9",38,1,1);
        add("0",39,1,1); add("-",45,1,1); add("=",46,1,1); add("BACK",42,1,1.8f);
        add("INS",73,1,1); add("HOME",74,1,1); add("PGUP",75,1,1);
        // QWERTY
        add("TAB",43,2,1.35f); add("Q",20,2,1); add("W",26,2,1); add("E",8,2,1); add("R",21,2,1);
        add("T",23,2,1); add("Y",28,2,1); add("U",24,2,1); add("I",12,2,1); add("O",18,2,1);
        add("P",19,2,1); add("[",47,2,1); add("]",48,2,1); add("\\",49,2,1.25f);
        add("DEL",76,2,1); add("END",77,2,1); add("PGDN",78,2,1);
        // Home row
        add("CAPS",57,3,1.6f); add("A",4,3,1); add("S",22,3,1); add("D",7,3,1); add("F",9,3,1);
        add("G",10,3,1); add("H",11,3,1); add("J",13,3,1); add("K",14,3,1); add("L",15,3,1);
        add(";",51,3,1); add("'",52,3,1); add("ENTER",40,3,2.0f);
        // Bottom letters/modifiers
        add("LSHIFT",225,4,1.8f); add("Z",29,4,1); add("X",27,4,1); add("C",6,4,1);
        add("V",25,4,1); add("B",5,4,1); add("N",17,4,1); add("M",16,4,1);
        add(",",54,4,1); add(".",55,4,1); add("/",56,4,1); add("RSHIFT",229,4,1.8f);
        add("UP",82,4,1); add("CTRL",224,5,1.35f); add("WIN", GUI_SCAN,5,1);
        add("ALT",226,5,1.25f); add("SPACE",44,5,5.4f); add("RALT",230,5,1.25f);
        add("MENU",101,5,1); add("RCTRL",228,5,1.35f); add("LEFT",80,5,1);
        add("DOWN",81,5,1); add("RIGHT",79,5,1);
        // Full numeric keypad
        add("NUM",83,6,1); add("KP/",84,6,1); add("KP*",85,6,1); add("KP-",86,6,1);
        add("KP7",95,6,1); add("KP8",96,6,1); add("KP9",97,6,1); add("KP+",87,6,1);
        add("KP4",92,6,1); add("KP5",93,6,1); add("KP6",94,6,1);
        add("KP1",89,6,1); add("KP2",90,6,1); add("KP3",91,6,1); add("KPENTER",88,6,1.6f);
        add("KP0",98,6,2); add("KP.",99,6,1);
        for(Key k:keys) if(!prefs.getBoolean(k.pref,true)) disabled.add(k.pref);
    }
    // SDL_SCANCODE_LGUI
    private static final int GUI_SCAN=227;
    private void add(String label,int scan,int row,float weight){keys.add(new Key(label,scan,row,weight));}
    private static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private int accent(){return new int[]{0xffff4657,0xff25b8ff,0xffffc34d,0xffdce6f2}[theme];}
    private int panel(){return new int[]{0xe91a0c12,0xe9091929,0xe91b160c,0xe927303b}[theme];}
    private int keyFill(){return new int[]{0xd92b1018,0xd90b2035,0xd92b2110,0xd93a4655}[theme];}
    private int ink(){return theme==GOLD?0xfffff0ca:0xfff3f7ff;}
    private void save(){
        SharedPreferences.Editor e=prefs.edit().putBoolean("enabled",enabled).putBoolean("collapsed",compact)
            .putBoolean("settings",settings).putInt("theme",theme).putInt("settingsPage",settingsPage)
            .putFloat("scale",scale).putFloat("opacity",opacity).putFloat("x",anchorX).putFloat("y",anchorY);
        for(Key k:keys)e.putBoolean(k.pref,!disabled.contains(k.pref));
        e.apply();
    }
    private boolean visible(Key k){return !disabled.contains(k.pref);}
    private float unit(){return dp(29)*scale;}
    private float gap(){return dp(3)*scale;}
    private float keyH(){return dp(29)*scale;}
    private float stride(){return unit()+gap();}
    private float headerH(){return dp(25)*scale;}
    private float rowWidth(int row){
        float w=0;int n=0;
        for(Key k:keys)if(k.row==row&&visible(k)){w+=unit()*k.weight+gap();n++;}
        return n==0?0:w-gap();
    }
    private float bodyW(){float w=0;for(int r=0;r<=6;r++)w=Math.max(w,rowWidth(r));return Math.max(dp(52),w+dp(8)*scale);}
    private float bodyH(){int rows=0;for(int r=0;r<=6;r++)if(rowWidth(r)>0)rows++;return headerH()+rows*(keyH()+gap())+dp(8)*scale;}
    private float totalW(){return !enabled?dp(44):compact?dp(146):bodyW();}
    private float totalH(){return !enabled?dp(44):compact?dp(65):bodyH();}
    private float left(){return clamp(anchorX,0,1)*Math.max(0,getWidth()-totalW());}
    private float top(){return clamp(anchorY,0,1)*Math.max(0,getHeight()-totalH());}
    private int withAlpha(int color,float amount){return (color&0x00ffffff)|(Math.round(((color>>>24)&255)*amount)<<24);}
    private void drawText(Canvas c,String s,float x,float y,float size,int color,boolean bold){
        p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(size);p.setFakeBoldText(bold);p.setTextAlign(Paint.Align.LEFT);c.drawText(s,x,y,p);p.setFakeBoldText(false);
    }
    private void button(Canvas c,RectF r,String label,int fill,int text){
        p.setStyle(Paint.Style.FILL);p.setColor(withAlpha(fill,opacity));c.drawRoundRect(r,dp(4),dp(4),p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(withAlpha(accent(),Math.min(1,opacity+.28f)));c.drawRoundRect(r,dp(4),dp(4),p);p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);p.setTextSize(Math.max(dp(7),Math.min(dp(10),r.height()*.48f)));p.setFakeBoldText(true);p.setColor(text);
        Paint.FontMetrics fm=p.getFontMetrics();c.drawText(label,r.centerX(),r.centerY()-(fm.ascent+fm.descent)/2,p);p.setFakeBoldText(false);
    }
    private RectF boardRect(float x,float y){return new RectF(x,y,x+bodyW(),y+bodyH());}
    private RectF gearRect(float x,float y){return new RectF(x+bodyW()-dp(47)*scale,y+dp(2)*scale,x+bodyW()-dp(27)*scale,y+headerH()-dp(2)*scale);}
    private RectF compactRect(float x,float y){return new RectF(x+bodyW()-dp(25)*scale,y+dp(2)*scale,x+bodyW()-dp(13)*scale,y+headerH()-dp(2)*scale);}
    private RectF hideRect(float x,float y){return new RectF(x+bodyW()-dp(12)*scale,y+dp(2)*scale,x+bodyW()-dp(2)*scale,y+headerH()-dp(2)*scale);}
    private RectF keyRect(Key key,float x,float y){
        float xx=x+dp(4)*scale;
        for(Key k:keys)if(k.row==key.row&&visible(k)){
            if(k==key)return new RectF(xx,y+headerH()+rowOffset(key.row),xx+unit()*k.weight,y+headerH()+rowOffset(key.row)+keyH());
            xx+=unit()*k.weight+gap();
        }
        return new RectF();
    }
    private float rowOffset(int row){float off=dp(3)*scale;for(int r=0;r<row;r++)if(rowWidth(r)>0)off+=keyH()+gap();else{}return off;}
    private int keyTextColor(Key k){return pressed.containsValue(k)?0xff07111f:ink();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float x=left(),y=top();
        if(!enabled){button(c,new RectF(x,y,x+dp(42),y+dp(42)),"⌨",accent(),0xff06111d);return;}
        if(compact){
            p.setStyle(Paint.Style.FILL);p.setColor(withAlpha(panel(),opacity));c.drawRoundRect(x,y,x+dp(146),y+dp(65),dp(8),dp(8),p);
            drawText(c,"WARU MINI",x+dp(6),y+dp(13),dp(9),accent(),true);
            button(c,new RectF(x+dp(117),y+dp(3),x+dp(142),y+dp(19)),"↗",accent(),0xff06111d);
            String[] labs={"W","A","S","D","↑","←","↓","→"};int[] scans={26,4,22,7,82,80,81,79};
            float[][] pos={{24,20},{4,39},{24,39},{44,39},{103,20},{83,39},{103,39},{123,39}};
            for(int i=0;i<labs.length;i++)button(c,new RectF(x+dp(pos[i][0]),y+dp(pos[i][1]),x+dp(pos[i][0]+18),y+dp(pos[i][1]+19)),labs[i],keyFill(),containsScan(scans[i])?0xff07111f:ink());
            return;
        }
        p.setStyle(Paint.Style.FILL);p.setColor(withAlpha(panel(),opacity));c.drawRoundRect(x,y,x+bodyW(),y+bodyH(),dp(8),dp(8),p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(withAlpha(accent(),Math.min(1,opacity+.2f)));c.drawRoundRect(x,y,x+bodyW(),y+bodyH(),dp(8),dp(8),p);p.setStyle(Paint.Style.FILL);
        drawText(c,"WARU • "+THEMES[theme],x+dp(5),y+headerH()*.72f,dp(9)*scale,accent(),true);
        button(c,gearRect(x,y),"⚙",accent(),0xff06111d);
        button(c,compactRect(x,y),"–",accent(),0xff06111d);
        button(c,hideRect(x,y),"×",accent(),0xff06111d);
        for(Key k:keys)if(visible(k)){
            RectF r=keyRect(k,x,y);
            int fill=pressed.containsValue(k)?accent():keyFill();
            button(c,r,k.label,fill,keyTextColor(k));
        }
        if(settings)drawSettings(c,x,y);
    }
    private boolean containsScan(int scan){for(Key k:keys)if(k.scan==scan&&visible(k))return true;return false;}
    private RectF settingsRect(float x,float y){float w=Math.min(getWidth()-dp(8),dp(300)),h=Math.min(getHeight()-dp(8),dp(250));float sx=Math.max(dp(4),Math.min(x,getWidth()-w-dp(4))),sy=y-h-dp(6);if(sy<dp(4))sy=Math.min(getHeight()-h-dp(4),y+totalH()+dp(4));return new RectF(sx,sy,sx+w,sy+h);}
    private void drawSettings(Canvas c,float x,float y){
        RectF r=settingsRect(x,y);p.setStyle(Paint.Style.FILL);p.setColor(0xf21a2330);c.drawRoundRect(r,dp(8),dp(8),p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(accent());c.drawRoundRect(r,dp(8),dp(8),p);p.setStyle(Paint.Style.FILL);
        float sx=r.left,sy=r.top,w=r.width();
        drawText(c,"WARU KEY SETTINGS",sx+dp(8),sy+dp(15),dp(10),ink(),true);
        String[] names={"RED","BLUE","GOLD","SILVER"};
        for(int i=0;i<4;i++)button(c,new RectF(sx+dp(6+i*57),sy+dp(21),sx+dp(60+i*57),sy+dp(41)),names[i],i==theme?accent():keyFill(),ink());
        button(c,new RectF(sx+dp(6),sy+dp(46),sx+dp(34),sy+dp(66)),"S−",keyFill(),ink());
        drawText(c,"SIZE "+Math.round(scale*100)+"%",sx+dp(39),sy+dp(59),dp(9),ink(),true);
        button(c,new RectF(sx+dp(95),sy+dp(46),sx+dp(123),sy+dp(66)),"S+",keyFill(),ink());
        button(c,new RectF(sx+dp(132),sy+dp(46),sx+dp(160),sy+dp(66)),"A−",keyFill(),ink());
        drawText(c,"ALPHA",sx+dp(164),sy+dp(59),dp(8),ink(),true);
        button(c,new RectF(sx+dp(208),sy+dp(46),sx+dp(236),sy+dp(66)),"A+",keyFill(),ink());
        button(c,new RectF(sx+w-dp(58),sy+dp(46),sx+w-dp(6),sy+dp(66)),"DONE",accent(),ink());
        drawText(c,"KEYS (tap to toggle • off keys take no space)",sx+dp(8),sy+dp(82),dp(8),ink(),true);
        int start=settingsPage*PAGE_SIZE,end=Math.min(keys.size(),start+PAGE_SIZE);
        float colW=(w-dp(12))/3f;
        for(int i=start;i<end;i++){
            int j=i-start,row=j/3,col=j%3;
            float bx=sx+dp(5)+col*colW,by=sy+dp(88)+row*dp(17);
            Key k=keys.get(i);boolean on=visible(k);
            button(c,new RectF(bx,by,bx+colW-dp(2),by+dp(15)),(on?"✓ ":"○ ")+k.label,on?keyFill():0xff343943,on?ink():0xffaab2bd);
        }
        float navY=Math.min(r.bottom-dp(20),sy+dp(88)+((PAGE_SIZE+2)/3)*dp(17));
        button(c,new RectF(sx+dp(6),navY,sx+dp(55),navY+dp(17)),"PREV",keyFill(),ink());
        drawText(c,(settingsPage+1)+" / "+Math.max(1,(keys.size()+PAGE_SIZE-1)/PAGE_SIZE),sx+dp(66),navY+dp(12),dp(9),ink(),true);
        button(c,new RectF(sx+w-dp(55),navY,sx+w-dp(6),navY+dp(17)),"NEXT",keyFill(),ink());
    }
    private void releaseAll(){for(Key k:pressed.values())GeneralsZHActivity.sendVirtualKey(k.scan,false);pressed.clear();}
    void releaseHeldKeys(){releaseAll();}
    private boolean down(MotionEvent e,int index){
        float tx=e.getX(index),ty=e.getY(index),x=left(),y=top();
        if(!enabled){if(new RectF(x,y,x+dp(44),y+dp(44)).contains(tx,ty)){enabled=true;compact=false;save();invalidate();return true;}return false;}
        if(compact){
            if(new RectF(x+dp(115),y,x+dp(146),y+dp(22)).contains(tx,ty)){compact=false;save();invalidate();return true;}
            if(ty<y+dp(19)){dragPointer=e.getPointerId(index);dragDx=tx-x;dragDy=ty-y;return true;}
            int[] scans={26,4,22,7,82,80,81,79};float[][] pos={{24,20},{4,39},{24,39},{44,39},{103,20},{83,39},{103,39},{123,39}};
            for(int i=0;i<scans.length;i++)if(new RectF(x+dp(pos[i][0]),y+dp(pos[i][1]),x+dp(pos[i][0]+18),y+dp(pos[i][1]+19)).contains(tx,ty)){
                for(Key k:keys)if(k.scan==scans[i]&&visible(k)){pressed.put(e.getPointerId(index),k);GeneralsZHActivity.sendVirtualKey(k.scan,true);invalidate();return true;}
            }
            return new RectF(x,y,x+totalW(),y+totalH()).contains(tx,ty);
        }
        if(settings){
            RectF r=settingsRect(x,y);if(!r.contains(tx,ty)){settings=false;save();invalidate();return true;}
            float lx=tx-r.left,ly=ty-r.top,w=r.width();
            if(ly>=dp(19)&&ly<=dp(43)){int t=(int)(lx/dp(57));if(t>=0&&t<4)theme=t;save();invalidate();return true;}
            if(ly>=dp(44)&&ly<=dp(69)){
                if(lx<dp(36))scale=clamp(scale-.07f,.48f,1.25f);
                else if(lx<dp(128)&&lx>dp(88))scale=clamp(scale+.07f,.48f,1.25f);
                else if(lx>=dp(130)&&lx<dp(163))opacity=clamp(opacity-.07f,.20f,.95f);
                else if(lx>=dp(205)&&lx<dp(240))opacity=clamp(opacity+.07f,.20f,.95f);
                else if(lx>w-dp(62)){settings=false;}
                save();invalidate();return true;
            }
            if(ly>=dp(86)){
                int start=settingsPage*PAGE_SIZE,end=Math.min(keys.size(),start+PAGE_SIZE);
                float colW=(w-dp(12))/3f;
                int col=(int)((lx-dp(5))/colW),row=(int)((ly-dp(88))/dp(17));
                if(row>=0&&col>=0&&col<3){int idx=start+row*3+col;if(idx>=start&&idx<end){Key k=keys.get(idx);if(visible(k))disabled.add(k.pref);else disabled.remove(k.pref);for(Map.Entry<Integer,Key> en:new ArrayList<>(pressed.entrySet()))if(en.getValue()==k){GeneralsZHActivity.sendVirtualKey(k.scan,false);pressed.remove(en.getKey());}save();invalidate();return true;}}
                int navRow=(PAGE_SIZE+2)/3,navY=88+navRow*17;
                if(ly>=dp(navY)&&ly<=dp(navY+22)){
                    if(lx<dp(60))settingsPage=Math.max(0,settingsPage-1);
                    else if(lx>w-dp(60))settingsPage=Math.min((keys.size()-1)/PAGE_SIZE,settingsPage+1);
                    save();invalidate();return true;
                }
            }
            return true;
        }
        if(gearRect(x,y).contains(tx,ty)){settings=true;settingsPage=0;invalidate();return true;}
        if(compactRect(x,y).contains(tx,ty)){compact=true;save();invalidate();return true;}
        if(hideRect(x,y).contains(tx,ty)){enabled=false;releaseAll();save();invalidate();return true;}
        if(ty>=y&&ty<y+headerH()){dragPointer=e.getPointerId(index);dragDx=tx-x;dragDy=ty-y;return true;}
        for(Key k:keys)if(visible(k)&&keyRect(k,x,y).contains(tx,ty)){
            pressed.put(e.getPointerId(index),k);GeneralsZHActivity.sendVirtualKey(k.scan,true);invalidate();return true;
        }
        return boardRect(x,y).contains(tx,ty);
    }
    private void up(MotionEvent e,int index){
        int id=e.getPointerId(index);Key k=pressed.remove(id);if(k!=null)GeneralsZHActivity.sendVirtualKey(k.scan,false);
        if(id==dragPointer)dragPointer=-1;save();invalidate();
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        int action=e.getActionMasked(),index=e.getActionIndex();
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN)return down(e,index);
        if(action==MotionEvent.ACTION_MOVE){
            if(dragPointer!=-1){int idx=e.findPointerIndex(dragPointer);if(idx>=0){float mx=Math.max(1,getWidth()-totalW()),my=Math.max(1,getHeight()-totalH());anchorX=clamp((e.getX(idx)-dragDx)/mx,0,1);anchorY=clamp((e.getY(idx)-dragDy)/my,0,1);invalidate();}return true;}
            return !pressed.isEmpty();
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP){up(e,index);return true;}
        if(action==MotionEvent.ACTION_CANCEL){releaseAll();dragPointer=-1;invalidate();return true;}
        return true;
    }
}
