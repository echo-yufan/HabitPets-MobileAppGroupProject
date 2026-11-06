package com.example.pet.feature.pet.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageView;

/** Original calf bitmap + vector outfit layers, all using the same aspect-fitted reference frame. */
public final class PetAvatarView extends AppCompatImageView {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path heart = new Path();
    private final Path shape = new Path();
    private final RectF shapeBounds = new RectF();
    private android.graphics.Picture outfitLayer = new android.graphics.Picture();
    private String outfit;
    private java.util.List<String> outfits = java.util.Collections.emptyList();
    private int mood = 80, satiety = 80;
    private float affection;
    private ValueAnimator animator;
    private Drawable foodArt;
    private boolean celebrating;
    private final Runnable clearFeedback = () -> { affection = 0; foodArt = null; celebrating = false; invalidate(); };
    public PetAvatarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        heart.moveTo(0, 30); heart.cubicTo(-85,-30,-65,-90,0,-45);
        heart.cubicTo(65,-90,85,-30,0,30);
    }
    public void setOutfit(String id) {
        setOutfits(id==null?java.util.Collections.emptyList():java.util.Collections.singletonList(id));
    }
    public void setOutfits(java.util.List<String> ids) {
        if (outfits.equals(ids)) return;
        outfits=java.util.Collections.unmodifiableList(new java.util.ArrayList<>(ids));
        outfit=ids.isEmpty()?null:ids.get(0);
        outfitLayer = new android.graphics.Picture();
        Canvas layer = outfitLayer.beginRecording(1000,1200);
        for(String slot:new String[]{"body","neck","head"})for(String id:outfits)
            if(slot.equals(com.example.pet.core.model.pet.PetOutfits.slotOf(id)))drawOutfit(layer,id);
        outfitLayer.endRecording();
        invalidate();
    }
    public String getOutfit() { return outfit; }
    public java.util.List<String> getOutfits() { return outfits; }
    public void setPetState(int mood,int satiety) { this.mood=mood;this.satiety=satiety;invalidate(); }
    public void playAffection() {
        foodArt = null; celebrating = false;
        animateFeedback(700);
    }
    public void playFeeding(int resourceId) {
        try { foodArt = androidx.appcompat.content.res.AppCompatResources.getDrawable(getContext(), resourceId); }
        catch (android.content.res.Resources.NotFoundException ignored) { foodArt = null; }
        celebrating = false; animateFeedback(1400);
    }
    public void playDressing() { foodArt = null; celebrating = true; animateFeedback(1100); }
    private void animateFeedback(long duration) {
        removeCallbacks(clearFeedback);
        if (animator != null) { animator.removeAllListeners(); animator.cancel(); }
        if (!ValueAnimator.areAnimatorsEnabled()) {
            affection = 1; invalidate(); postDelayed(clearFeedback, duration); return;
        }
        animator = ValueAnimator.ofFloat(1, 0); animator.setDuration(duration);
        animator.addUpdateListener(value -> { affection = (float)value.getAnimatedValue(); invalidate(); });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) { clearFeedback.run(); }
        });
        animator.start();
    }
    @Override protected void onDetachedFromWindow() {
        removeCallbacks(clearFeedback);
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
    @Override protected void onDraw(Canvas canvas) {
        Drawable cow = getDrawable();
        if (cow == null) return;
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        float scale = .70f * Math.min(width / cow.getIntrinsicWidth(), height / cow.getIntrinsicHeight());
        float w = cow.getIntrinsicWidth() * scale, h = cow.getIntrinsicHeight() * scale;
        canvas.save();
        canvas.translate(getPaddingLeft() + (width - w) / 2, getPaddingTop() + (height - h) * .40f);
        canvas.scale(w / 1000f, h / 1200f);
        if(affection>0&&ValueAnimator.areAnimatorsEnabled()) {
            float bounce=1+.025f*(float)Math.sin((1-affection)*Math.PI*2);
            canvas.scale(bounce,bounce,500,1100);
        }
        cow.setBounds(0, 0, 1000, 1200); cow.draw(canvas);
        canvas.drawPicture(outfitLayer);
        if(mood>=70||affection>0){oval(canvas,302,422,378,464,"#E88A75");oval(canvas,640,442,709,478,"#E88A75");}
        if(mood<=30){color("#80B9D2");shape.reset();shape.moveTo(678,416);shape.cubicTo(650,457,650,480,678,480);shape.cubicTo(707,480,707,457,678,416);canvas.drawPath(shape,paint);}
        if(satiety<=30){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(8);paint.setColor(Color.rgb(155,116,67));canvas.drawArc(420,815,590,930,0,80,false,paint);canvas.drawArc(445,845,565,915,0,80,false,paint);paint.setStyle(Paint.Style.FILL);}
        if (affection > 0) {
            if (foodArt != null) {
                float progress = 1-affection;
                float x = ValueAnimator.areAnimatorsEnabled() ? 810 - progress*310 : 650;
                float y = ValueAnimator.areAnimatorsEnabled() ? 800 - progress*260 : 650;
                canvas.save(); canvas.translate(x,y);
                foodArt.setBounds(-95,-95,95,95); foodArt.draw(canvas); canvas.restore();
            }
            if (celebrating) {
                sparkle(canvas,260,290); sparkle(canvas,780,500); sparkle(canvas,700,85);
            }
            canvas.save(); canvas.translate(790, 350 - (1-affection)*140); canvas.scale(.8f, .8f);
            color("#D85B6D"); canvas.drawPath(heart,paint); canvas.restore();
        }
        canvas.restore();
    }
    private void sparkle(Canvas c, float x, float y) {
        polygon(c,"#D5A148",x,y-30,x+10,y-10,x+30,y,x+10,y+10,x,y+30,x-10,y+10,x-30,y,x-10,y-10);
    }
    private void color(String hex) { paint.setColor(Color.parseColor(hex)); paint.setStyle(Paint.Style.FILL); }
    private void oval(Canvas c, float l,float t,float r,float b,String hex) { color(hex); c.drawOval(l,t,r,b,paint); }
    private void polygon(Canvas c, String hex, float... xy) {
        color(hex); shape.reset(); shape.moveTo(xy[0],xy[1]);
        for(int i=2;i<xy.length;i+=2)shape.lineTo(xy[i],xy[i+1]); shape.close(); c.drawPath(shape,paint);
    }
    private void round(Canvas c,float l,float t,float r,float b,float radius,String hex) {
        color(hex); shapeBounds.set(l,t,r,b); c.drawRoundRect(shapeBounds,radius,radius,paint);
    }
    private void drawOutfit(Canvas c, String outfit) {
        if (outfit == null) return;
        switch (outfit) {
            case "hat":
                round(c,345,45,650,190,50,"#DFC080"); oval(c,260,155,725,230,"#B18B50");
                round(c,345,133,650,168,8,"#6F8B61"); break;
            case "beret":
                oval(c,305,60,700,207,"#865B75"); round(c,350,163,660,204,18,"#684359");
                round(c,499,34,520,84,8,"#684359"); break;
            case "wizard":
                polygon(c,"#596493",325,185,528,3,660,185); oval(c,270,160,720,225,"#424E78");
                polygon(c,"#E8D495",514,62,530,85,555,88,534,107,538,133,514,119,490,133,494,107,474,88,500,85); break;
            case "crown":
                polygon(c,"#E2B54C",324,100,392,149,450,54,510,143,583,52,635,149,698,91,670,221,350,221);
                round(c,345,193,674,226,10,"#BC8936"); oval(c,490,166,530,201,"#D45F64"); break;
            case "bow": bow(c,500,674,1,"#CF665B"); break;
            case "ribbon": bow(c,680,206,.85f,"#BC7096");
                polygon(c,"#BC7096",671,211,716,215,734,310,691,280,664,307); break;
            case "scarf":
                round(c,300,644,706,718,28,"#719369");
                polygon(c,"#86A57B",560,695,660,703,699,884,586,905);
                polygon(c,"#E6D5A7",585,852,690,833,697,860,590,881); break;
            case "raincoat":
                polygon(c,"#E4B753",329,680,454,719,552,719,687,680,754,1024,268,1024);
                polygon(c,"#F5D68A",329,680,450,719,423,787,311,735);
                polygon(c,"#F5D68A",552,719,687,680,700,735,575,787);
                round(c,497,740,511,1024,4,"#A47C35");
                for(int y=790;y<1000;y+=65) oval(c,532,y,548,y+16,"#FFF1CF"); break;
            case "vest":
                polygon(c,"#597C83",345,692,452,731,505,808,555,731,665,692,706,1003,295,1003);
                polygon(c,"#E9D8B5",452,731,505,808,555,731,528,910,488,910);
                round(c,499,817,511,1003,4,"#395E65");
                for(int y=866;y<990;y+=54) oval(c,527,y,543,y+16,"#E9D8B5"); break;
        }
    }
    private void bow(Canvas c,float x,float y,float scale,String hex) {
        c.save(); c.translate(x,y); c.scale(scale,scale);
        polygon(c,hex,-10,0,-118,-60,-118,65,-10,25);
        polygon(c,hex,10,0,118,-60,118,65,10,25);
        round(c,-25,-17,25,40,14,"#A74D49"); c.restore();
    }
}
