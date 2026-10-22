package com.generalsx.zerohour;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.util.HashMap;
import java.util.Map;

/**
 * Draggable, translucent PC-style touch keyboard for the live SDL game surface.
 * Keys are delivered as real SDL keyboard events so the engine's existing
 * keyboard bindings (including modifier combinations) remain authoritative.
 */
final class WaruGameKeyboardOverlay extends View {
    private static final String PREFS = "waru_game_keyboard";
    private static final int RED=0, BLUE=1, GOLD=2, SILVER=3;
    private static final int[] SCAN = {41,43,20,26,8,21,30,31,32,224,4,22,7,9,10,33,34,225,29,27,6,25,44,226,80,82,81,79,40};
    private static final String[] LABEL = {"ESC","TAB","Q","W","E","R","1","2","3","CTRL","A","S","D","F","G","4","5","SHIFT","Z","X","C","V","SPACE","ALT","←","↑","↓","→","ENTER"};
    private static final int[] COL = {0,1,2,3,4,5,6,7,8,0,1,2,3,4,5,6,7,0,1,2,3,4,5,6,7,8,7,8,8};
    private static final int[] ROW = {0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,2,2,2,2,2,2,2,2,2,3,2,3};
    private final Paint p = new Paint(3);
    private final SharedPreferences prefs;
    private final Map<Integer,Integer> pressed = new HashMap<>();
    private float anchorX, anchorY, scale, opacity;
    private int theme;
    private boolean enabled, collapsed, settings;
    private int dragPointer = -1;
    private float dragDx, dragDy;
    private int heldPointer = -1;

    WaruGameKeyboardOverlay(Context context) {
        super(context);
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        enabled = prefs.getBoolean("enabled", true);
        collapsed = prefs.getBoolean("collapsed", false);
        theme = clamp(prefs.getInt("theme", BLUE), 0, 3);
        scale = clamp(prefs.getFloat("scale", 1f), .65f, 1.5f);
        opacity = clamp(prefs.getFloat("opacity", .68f), .30f, .90f);
        anchorX = prefs.getFloat("x", .035f);
        anchorY = prefs.getFloat("y", .50f);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setClickable(true);
    }

    private static float clamp(float v,float lo,float hi){ return Math.max(lo,Math.min(hi,v)); }
    private int dp(float v){ return Math.round(v*getResources().getDisplayMetrics().density); }
    private int accent(){ return new int[]{0xffff4657,0xff25b8ff,0xffffc34d,0xffdce6f2}[theme]; }
    private int panel(){ return new int[]{0xe91a0c12,0xe9091929,0xe91b160c,0xe927303b}[theme]; }
    private int keyFill(){ return new int[]{0xd92b1018,0xd90b2035,0xd92b2110,0xd93a4655}[theme]; }
    private int ink(){ return theme==GOLD?0xfffff0ca:theme==SILVER?0xfff7faff:0xfff3f7ff; }
    private void save(){
        prefs.edit().putBoolean("enabled",enabled).putBoolean("collapsed",collapsed)
            .putInt("theme",theme).putFloat("scale",scale).putFloat("opacity",opacity)
            .putFloat("x",anchorX).putFloat("y",anchorY).apply();
    }
    private float unit(){ return dp(38)*scale; }
    private float gap(){ return dp(4)*scale; }
    private float keyW(){ return unit(); }
    private float keyH(){ return dp(36)*scale; }
    private float stride(){ return keyW()+gap(); }
    private float headerH(){ return dp(29)*scale; }
    private float bodyW(){ return stride()*9-gap(); }
    private float bodyH(){ return headerH()+stride()*4+dp(4)*scale; }
    private float left(){ return clamp(anchorX,0,1)*Math.max(0,getWidth()-totalW()); }
    private float top(){ return clamp(anchorY,0,1)*Math.max(0,getHeight()-totalH()); }
    private float totalW(){ return collapsed||!enabled?dp(48):bodyW(); }
    private float totalH(){ return collapsed||!enabled?dp(48):bodyH(); }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float x=left(), y=top();
        if(!enabled){
            drawButton(c,new RectF(x,y,x+dp(46),y+dp(46)), "⌨", accent(), true);
            return;
        }
        if(collapsed){
            drawButton(c,new RectF(x,y,x+dp(46),y+dp(46)), "⌨", accent(), true);
            return;
        }
        float a=opacity;
        p.setColor(withAlpha(panel(),a)); p.setStyle(Paint.Style.FILL);
        c.drawRoundRect(x,y,x+bodyW(),y+bodyH(),dp(10),dp(10),p);
        p.setColor(withAlpha(accent(),Math.min(1,a+.18f))); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1.2f));
        c.drawRoundRect(x,y,x+bodyW(),y+bodyH(),dp(10),dp(10),p); p.setStyle(Paint.Style.FILL);
        text(c,"WARU  •  "+new String[]{"RED","BLUE","GOLD","SILVER"}[theme],x+dp(9),y+headerH()*.68f,dp(10)*scale,accent(),true);
        drawButton(c,new RectF(x+bodyW()-dp(68)*scale,y+dp(3)*scale,x+bodyW()-dp(46)*scale,y+headerH()-dp(3)*scale),"⚙",accent(),false);
        drawButton(c,new RectF(x+bodyW()-dp(43)*scale,y+dp(3)*scale,x+bodyW()-dp(24)*scale,y+headerH()-dp(3)*scale),"–",accent(),false);
        drawButton(c,new RectF(x+bodyW()-dp(21)*scale,y+dp(3)*scale,x+bodyW()-dp(4)*scale,y+headerH()-dp(3)*scale),"×",accent(),false);
        for(int i=0;i<LABEL.length;i++){
            float kw=keyW();
            if(i==22) kw=keyW()*2+gap();
            float kx=x+dp(4)*scale+COL[i]*stride();
            float ky=y+headerH()+dp(3)*scale+ROW[i]*stride();
            if(i==22) kx=x+dp(4)*scale+4*stride();
            if(i==27) kx=x+dp(4)*scale+8*stride();
            RectF r=new RectF(kx,ky,kx+kw,ky+keyH());
            boolean down=pressed.containsValue(SCAN[i]);
            drawButton(c,r,LABEL[i],down?accent():0,keyInk(down));
        }
        if(settings) drawSettings(c,x,y);
    }

    private int keyInk(boolean down){ return down?0xff07111f:ink(); }
    private int withAlpha(int color,float amount){ return (color&0x00ffffff)|(Math.round(((color>>>24)&255)*amount)<<24); }
    private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold){
        p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(size);p.setFakeBoldText(bold);p.setTextAlign(Paint.Align.LEFT);c.drawText(s,x,y,p);p.setFakeBoldText(false);
    }
    private void drawButton(Canvas c,RectF r,String label,int border,boolean bright){
        p.setStyle(Paint.Style.FILL);p.setColor(withAlpha(bright?accent():keyFill(),opacity));c.drawRoundRect(r,dp(6),dp(6),p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1)*scale);p.setColor(withAlpha(border!=0?border:accent(),Math.min(1,opacity+.25f)));c.drawRoundRect(r,dp(6),dp(6),p);p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);p.setTextSize(Math.max(dp(8),dp(label.length()>4?8:11)*scale));p.setFakeBoldText(true);p.setColor(bright?0xff06111d:ink());
        Paint.FontMetrics fm=p.getFontMetrics();c.drawText(label,r.centerX(),r.centerY()-(fm.ascent+fm.descent)/2,p);p.setFakeBoldText(false);
    }
    private void drawButton(Canvas c,RectF r,String label,int border,int textColor){
        p.setStyle(Paint.Style.FILL);p.setColor(withAlpha(keyFill(),opacity));c.drawRoundRect(r,dp(6),dp(6),p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1)*scale);p.setColor(withAlpha(border!=0?border:accent(),Math.min(1,opacity+.25f)));c.drawRoundRect(r,dp(6),dp(6),p);p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);p.setTextSize(Math.max(dp(8),dp(label.length()>4?8:11)*scale));p.setFakeBoldText(true);p.setColor(textColor);
        Paint.FontMetrics fm=p.getFontMetrics();c.drawText(label,r.centerX(),r.centerY()-(fm.ascent+fm.descent)/2,p);p.setFakeBoldText(false);
    }
    private void drawSettings(Canvas c,float x,float y){
        float w=dp(236), h=dp(118), sx=Math.max(dp(4),Math.min(x, getWidth()-w-dp(4))), sy=Math.max(dp(4),y-h-dp(8));
        p.setColor(0xf21a2330);p.setStyle(Paint.Style.FILL);c.drawRoundRect(sx,sy,sx+w,sy+h,dp(10),dp(10),p);
        p.setColor(accent());p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));c.drawRoundRect(sx,sy,sx+w,sy+h,dp(10),dp(10),p);p.setStyle(Paint.Style.FILL);
        text(c,"COLOR",sx+dp(9),sy+dp(16),dp(9),ink(),true);
        String[] names={"RED","BLUE","GOLD","SILVER"};
        for(int i=0;i<4;i++) drawButton(c,new RectF(sx+dp(8+i*56),sy+dp(22),sx+dp(60+i*56),sy+dp(45)),names[i],i==theme?accent():0,i==theme?accent():ink());
        text(c,"SIZE",sx+dp(9),sy+dp(61),dp(9),ink(),true);
        drawButton(c,new RectF(sx+dp(43),sy+dp(49),sx+dp(70),sy+dp(72)),"−",0,ink());
        text(c,Math.round(scale*100)+"%",sx+dp(77),sy+dp(64),dp(10),ink(),true);
        drawButton(c,new RectF(sx+dp(95),sy+dp(49),sx+dp(122),sy+dp(72)),"+",0,ink());
        text(c,"ALPHA",sx+dp(132),sy+dp(61),dp(9),ink(),true);
        drawButton(c,new RectF(sx+dp(174),sy+dp(49),sx+dp(201),sy+dp(72)),"−",0,ink());
        drawButton(c,new RectF(sx+dp(205),sy+dp(49),sx+dp(232),sy+dp(72)),"+",0,ink());
        drawButton(c,new RectF(sx+dp(8),sy+dp(82),sx+dp(78),sy+dp(108)), "COMPACT",0,ink());
        drawButton(c,new RectF(sx+dp(84),sy+dp(82),sx+dp(153),sy+dp(108)), "HIDE",0,ink());
        drawButton(c,new RectF(sx+dp(159),sy+dp(82),sx+dp(232),sy+dp(108)), "DONE",accent(),ink());
    }

    private RectF keyRect(int i,float x,float y){
        float kw=keyW(); if(i==22)kw=keyW()*2+gap();
        float kx=x+dp(4)*scale+COL[i]*stride(); if(i==22)kx=x+dp(4)*scale+4*stride();if(i==27)kx=x+dp(4)*scale+8*stride();
        float ky=y+headerH()+dp(3)*scale+ROW[i]*stride();
        return new RectF(kx,ky,kx+kw,ky+keyH());
    }
    private RectF gearRect(float x,float y){return new RectF(x+bodyW()-dp(68)*scale,y+dp(3)*scale,x+bodyW()-dp(46)*scale,y+headerH()-dp(3)*scale);}
    private RectF collapseRect(float x,float y){return new RectF(x+bodyW()-dp(43)*scale,y+dp(3)*scale,x+bodyW()-dp(24)*scale,y+headerH()-dp(3)*scale);}
    private RectF closeRect(float x,float y){return new RectF(x+bodyW()-dp(21)*scale,y+dp(3)*scale,x+bodyW()-dp(4)*scale,y+headerH()-dp(3)*scale);}
    private RectF settingsRect(float x,float y){
        float w=dp(236),h=dp(118);float sx=Math.max(dp(4),Math.min(x,getWidth()-w-dp(4))),sy=Math.max(dp(4),y-h-dp(8));return new RectF(sx,sy,sx+w,sy+h);
    }
    private void releaseAll(){for(int scan:pressed.values()) GeneralsZHActivity.sendVirtualKey(scan,false);pressed.clear();}
    private boolean hit(float x,float y,RectF r){return r.contains(x,y);}
    private int hitKey(float tx,float ty,float x,float y){for(int i=0;i<LABEL.length;i++)if(keyRect(i,x,y).contains(tx,ty))return i;return -1;}
    private void changeTheme(int t){theme=t;save();invalidate();}
    private boolean down(MotionEvent e,int index){
        float tx=e.getX(index),ty=e.getY(index),x=left(),y=top();
        if(!enabled||collapsed){if(new RectF(x,y,x+dp(48),y+dp(48)).contains(tx,ty)){enabled=true;collapsed=false;save();invalidate();return true;}return false;}
        if(settings){
            RectF r=settingsRect(x,y); if(!r.contains(tx,ty)){settings=false;invalidate();return true;}
            float lx=tx-r.left,ly=ty-r.top;
            if(ly>=dp(20)&&ly<=dp(47)){int t=(int)(lx/dp(56));if(t>=0&&t<4)changeTheme(t);return true;}
            if(ly>=dp(48)&&ly<=dp(75)){
                if(lx>=dp(40)&&lx<=dp(75))scale=clamp(scale-.1f,.65f,1.5f);
                else if(lx>=dp(92)&&lx<=dp(126))scale=clamp(scale+.1f,.65f,1.5f);
                else if(lx>=dp(170)&&lx<=dp(203))opacity=clamp(opacity-.08f,.30f,.90f);
                else if(lx>=dp(202)&&lx<=dp(236))opacity=clamp(opacity+.08f,.30f,.90f);
                save();invalidate();return true;
            }
            if(ly>=dp(80)){
                if(lx<dp(80)){collapsed=true;settings=false;}
                else if(lx<dp(155)){enabled=false;settings=false;releaseAll();}
                else settings=false;
                save();invalidate();return true;
            }
            return true;
        }
        if(hit(tx,ty,gearRect(x,y))){settings=true;invalidate();return true;}
        if(hit(tx,ty,collapseRect(x,y))){collapsed=true;save();invalidate();return true;}
        if(hit(tx,ty,closeRect(x,y))){enabled=false;releaseAll();save();invalidate();return true;}
        if(ty>=y&&ty<y+headerH()){
            dragPointer=e.getPointerId(index);dragDx=tx-x;dragDy=ty-y;return true;
        }
        int k=hitKey(tx,ty,x,y);
        if(k>=0){int id=e.getPointerId(index);pressed.put(id,SCAN[k]);GeneralsZHActivity.sendVirtualKey(SCAN[k],true);invalidate();return true;}
        return new RectF(x,y,x+bodyW(),y+bodyH()).contains(tx,ty);
    }
    private void up(MotionEvent e,int index){
        int id=e.getPointerId(index);Integer scan=pressed.remove(id);if(scan!=null)GeneralsZHActivity.sendVirtualKey(scan,false);
        if(id==dragPointer)dragPointer=-1;save();invalidate();
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        int action=e.getActionMasked(),index=e.getActionIndex();
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN)return down(e,index);
        if(action==MotionEvent.ACTION_MOVE){
            if(dragPointer!=-1){int idx=e.findPointerIndex(dragPointer);if(idx>=0){float maxX=Math.max(1,getWidth()-totalW()),maxY=Math.max(1,getHeight()-totalH());anchorX=clamp((e.getX(idx)-dragDx)/maxX,0,1);anchorY=clamp((e.getY(idx)-dragDy)/maxY,0,1);invalidate();}return true;}
            return !pressed.isEmpty();
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP){up(e,index);return true;}
        if(action==MotionEvent.ACTION_CANCEL){releaseAll();dragPointer=-1;invalidate();return true;}
        return true;
    }
}
