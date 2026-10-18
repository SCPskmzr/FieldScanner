package com.fieldscanner.app.display.zones

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import com.fieldscanner.app.BottomStatusMode
import com.fieldscanner.app.SignalAnalysis
import com.fieldscanner.app.SignalExtremum
import com.fieldscanner.app.SignalSample
import com.fieldscanner.app.data.finding.ScanVisual
import kotlin.math.abs
import kotlin.math.sin

/** Owns the lower signal zone and its capture and analysis animations. */
internal class BottomStatusRenderer(
    private val context: Context,
    private val zone: Path
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val signalPath = Path(); private val upperEchoPath = Path(); private val lowerEchoPath = Path()
    private var frozenPath: Path? = null; private var hasRenderedPath = false
    private var renderedSamples: List<SignalSample> = emptyList(); private var frozenSamples: List<SignalSample> = emptyList(); private var frozenExtrema: List<SignalExtremum> = emptyList()
    private var transitionStart = 0f; private var transitionTarget = 0f; private var transitionStartedAt = SystemClock.uptimeMillis()
    private var timeline = SystemClock.uptimeMillis().toFloat(); private var timelineUpdatedAt = SystemClock.uptimeMillis(); private var analysisStartedAt = 0L; private var analysisResultStartedAt = 0L
    private var resultSamples: List<SignalSample>? = null; private var resultLoadAttempted = false; private var resultVisual: ScanVisual = ScanVisual.UNKNOWN_OBJECT

    /** Selects which catalogue outline the next analysis result morphs into. */
    fun setResultVisual(visual: ScanVisual) { if (visual != resultVisual) { resultVisual = visual; resultSamples = null; resultLoadAttempted = false } }

    fun setMode(mode: BottomStatusMode) {
        when (mode) { BottomStatusMode.CAMERA -> startTransition(1f); BottomStatusMode.IDLE -> startTransition(0f); BottomStatusMode.ANALYZING -> analysisStartedAt = SystemClock.uptimeMillis(); BottomStatusMode.ANALYSIS_RESULT -> analysisResultStartedAt = SystemClock.uptimeMillis(); else -> Unit }
        if (mode == BottomStatusMode.IDLE || mode == BottomStatusMode.CAMERA || mode == BottomStatusMode.HIDDEN) clearFrozenSignal()
    }
    fun capture() { frozenPath = if (hasRenderedPath) Path(signalPath) else null; frozenSamples = renderedSamples.toList(); frozenExtrema = SignalAnalysis.selectKeyExtrema(frozenSamples, 5, 72f, 5f) }
    fun drawGlow(canvas: Canvas) { canvas.save(); canvas.clipPath(zone); paint.style=Paint.Style.STROKE; paint.color=0x75FF4F0B; paint.strokeWidth=30f; paint.maskFilter=BlurMaskFilter(30f,BlurMaskFilter.Blur.NORMAL); canvas.drawPath(zone,paint); paint.color=0xB8FF7B19.toInt(); paint.strokeWidth=13f; paint.maskFilter=BlurMaskFilter(9f,BlurMaskFilter.Blur.NORMAL); canvas.drawPath(zone,paint); canvas.restore(); paint.maskFilter=null; paint.color=0xFFFFB35C.toInt(); paint.strokeWidth=2.5f; canvas.drawPath(zone,paint) }
    fun draw(canvas: Canvas, mode: BottomStatusMode, frozenAt: Long?, now: Long) { if(mode==BottomStatusMode.HIDDEN)return; canvas.save();canvas.clipPath(zone);drawGrid(canvas);if(mode==BottomStatusMode.DELETE_CONFIRMATION){drawTrashSymbol(canvas);canvas.restore();return};val level=cameraLevel(now);val live=mode==BottomStatusMode.IDLE||mode==BottomStatusMode.CAMERA;if(mode==BottomStatusMode.ANALYSIS_RESULT){if(!drawResultMorph(canvas,now)){drawWave(canvas,frozenAt?:now,lerp(28f,48f,level),true,frozenPath);drawSymbols(canvas)}}else{drawWave(canvas,if(live)animatedTime(now,level)else frozenAt?:now,lerp(28f,48f,level),false,if(live)null else frozenPath);when(mode){BottomStatusMode.CAPTURE_FLASH->drawFlash(canvas,now,frozenAt);BottomStatusMode.ANALYZING->drawAnalysis(canvas,now);else->Unit}};canvas.restore() }
    private fun drawTrashSymbol(canvas: Canvas) { paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND;paint.strokeWidth=7f;paint.color=0xFFFF4A3D.toInt();canvas.drawRoundRect(558f,570f,700f,686f,12f,12f,paint);canvas.drawLine(540f,550f,718f,550f,paint);canvas.drawLine(600f,530f,658f,530f,paint);canvas.drawLine(615f,598f,615f,660f,paint);canvas.drawLine(643f,598f,643f,660f,paint);paint.strokeCap=Paint.Cap.BUTT;paint.strokeJoin=Paint.Join.MITER }
    private fun clearFrozenSignal(){frozenPath=null;frozenSamples=emptyList();frozenExtrema=emptyList()}
    private fun drawGrid(canvas:Canvas){paint.style=Paint.Style.STROKE;paint.strokeWidth=.8f;paint.color=0x2EFFA35A;var x=290f;while(x<=970f){canvas.drawLine(x,520f,x,720f,paint);x+=58f};var y=548f;while(y<=710f){canvas.drawLine(280f,y,970f,y,paint);y+=39f}}
    private fun drawWave(canvas:Canvas,time:Long,amplitude:Float,crisp:Boolean,frozen:Path?){if(frozen!=null)signalPath.set(frozen)else buildWave(time,amplitude);drawSignalPath(canvas,crisp)}
    private fun drawResultMorph(canvas: Canvas, now: Long): Boolean { val source=frozenSamples;if(source.size<2)return false;val target=resultSamples ?: run { if(!resultLoadAttempted){resultLoadAttempted=true;resultSamples=ScanOutlineAsset.loadSamples(context,resultVisual,source.size)};resultSamples } ?: return false;if(target.size!=source.size)return false;val progress=((now-analysisResultStartedAt).toFloat()/RESULT_MORPH_DURATION_MILLIS).coerceIn(0f,1f);val samples=ScanMorphGeometry.interpolate(source,target,progress);signalPath.reset();samples.forEachIndexed { index,sample -> if(index==0)signalPath.moveTo(sample.x,sample.y) else signalPath.lineTo(sample.x,sample.y) };drawSignalPath(canvas,false);return true }
    private fun drawSignalPath(canvas:Canvas,crisp:Boolean){paint.style=Paint.Style.STROKE;paint.maskFilter=if(crisp)null else BlurMaskFilter(5f,BlurMaskFilter.Blur.NORMAL);paint.strokeWidth=if(crisp)3.5f else 7f;paint.color=if(crisp)0xFFFF8E25.toInt()else 0xB0FF7A1A.toInt();canvas.drawPath(signalPath,paint);paint.maskFilter=null;paint.strokeWidth=if(crisp)2f else 2.2f;paint.color=if(crisp)0xFFFFE4B5.toInt()else 0xFFFFC06A.toInt();canvas.drawPath(signalPath,paint)}
    private fun buildWave(time:Long,amplitude:Float){signalPath.reset();val samples=ArrayList<SignalSample>();val bucket=time/2533L;val blend=(time%2533L)/2533f;var x=306f;while(x<=953f){val index=((x-306f)/8f).toInt();val current=SignalAnalysis.signalValue(index,bucket,amplitude);val next=SignalAnalysis.signalValue(index,bucket+1,amplitude);val y=guideY(x)+current+(next-current)*smooth(blend);if(x==306f)signalPath.moveTo(x,y)else signalPath.lineTo(x,y);samples+=SignalSample(x,y);x+=8f};renderedSamples=samples;hasRenderedPath=true}
    private fun drawAnalysis(canvas:Canvas,now:Long){val samples=if(frozenSamples.isNotEmpty())frozenSamples else renderedSamples;if(samples.size<3)return;val elapsed=(now-analysisStartedAt).coerceAtLeast(0L)%3800L;val scanX=if(elapsed in 300L until 2300L)306f+647f*(elapsed-300L)/2000f else null;drawEchoes(canvas,samples,now,scanX);scanX?.let{drawScanner(canvas,it);drawCursor(canvas,SignalAnalysis.sampleAtX(samples,it),1f)};frozenExtrema.forEach{drawExtremum(canvas,it,elapsed)}}
    private fun drawEchoes(canvas:Canvas,samples:List<SignalSample>,now:Long,scanX:Float?){upperEchoPath.reset();lowerEchoPath.reset();samples.forEachIndexed{index,sample->val collapse=scanX?.let{smooth((1f-abs(sample.x-it)/105f).coerceIn(0f,1f))}?:0f;val offset=(8f+sin(sample.x*.051f+now/4800f)*1.2f)*(1f-collapse);if(index==0){upperEchoPath.moveTo(sample.x,sample.y-offset);lowerEchoPath.moveTo(sample.x,sample.y+offset)}else{upperEchoPath.lineTo(sample.x,sample.y-offset);lowerEchoPath.lineTo(sample.x,sample.y+offset)}};paint.style=Paint.Style.STROKE;paint.strokeWidth=1.1f;paint.color=0x4CFFB05A;canvas.drawPath(upperEchoPath,paint);canvas.drawPath(lowerEchoPath,paint)}
    private fun drawScanner(canvas:Canvas,x:Float){paint.style=Paint.Style.STROKE;paint.strokeWidth=1.5f;paint.color=0xC0FFC06A.toInt();canvas.drawLine(x,540f,x,710f,paint)}
    private fun drawCursor(canvas:Canvas,sample:SignalSample,opacity:Float){paint.style=Paint.Style.FILL;paint.color=Color.argb((255*opacity).toInt(),255,228,181);canvas.drawCircle(sample.x,sample.y,4.2f,paint)}
    private fun drawExtremum(canvas:Canvas,extremum:SignalExtremum,elapsed:Long){val discovery=300L+(2000f*(extremum.sample.x-306f)/647f).toLong();if(elapsed !in discovery until 3500L)return;val opacity=if(elapsed<3100L)1f else 1f-(elapsed-3100L)/400f;paint.style=Paint.Style.STROKE;paint.strokeWidth=1.5f;paint.color=Color.argb((255*opacity).toInt(),255,179,92);val direction=if(extremum.isMaximum)-1f else 1f;canvas.drawLine(extremum.sample.x,extremum.sample.y,extremum.sample.x,extremum.sample.y+direction*8f,paint)}
    private fun drawFlash(canvas:Canvas,now:Long,frozenAt:Long?){val strength=(1f-(now-(frozenAt?:now))/1100f).coerceIn(0f,1f);canvas.drawColor(Color.argb((185*strength).toInt(),255,113,19))}
    private fun drawSymbols(canvas:Canvas){paint.style=Paint.Style.FILL;paint.textSize=13f;paint.color=0xFFFFE4B5.toInt();canvas.drawText("<:| 7A *|: 03 /\\ 1E :: 4F |*>",402f,625f,paint);canvas.drawText(".. 0C :| 91 // 2B :: 6E ..",462f,692f,paint)}
    private fun startTransition(target:Float){val now=SystemClock.uptimeMillis();transitionStart=cameraLevel(now);transitionTarget=target;transitionStartedAt=now};private fun cameraLevel(now:Long)=lerp(transitionStart,transitionTarget,smooth(((now-transitionStartedAt)/3000f).coerceIn(0f,1f)));private fun animatedTime(now:Long,level:Float):Long{timeline+=(now-timelineUpdatedAt).coerceAtLeast(0L)*lerp(1f,2533f/1320f,level);timelineUpdatedAt=now;return timeline.toLong()};private fun guideY(x:Float)=if(x<=630f)637f+(668f-637f)*(630f-x)/324f else 637f+(598f-637f)*(x-630f)/323f;private fun lerp(from:Float,to:Float,fraction:Float)=from+(to-from)*fraction;private fun smooth(value:Float)=value*value*(3f-2f*value)
    private companion object { const val RESULT_MORPH_DURATION_MILLIS = 1_800f }
}
