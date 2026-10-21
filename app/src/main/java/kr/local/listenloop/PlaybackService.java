package kr.local.listenloop;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.session.*;
import com.google.common.util.concurrent.*;
import java.util.List;

@androidx.media3.common.util.UnstableApi
public class PlaybackService extends MediaSessionService {
    public static final SessionCommand AUDITION=new SessionCommand("audition",Bundle.EMPTY),RESTORE=new SessionCommand("restore",Bundle.EMPTY);
    private long lastListenActivity=0;private long auditionPlayed=0;private boolean auditionHeard=false;private boolean audition=false;private String savedId="";private long savedPosition=0;
    public static final SessionCommand CONFIGURE=new SessionCommand("configure",Bundle.EMPTY);
    private StudyStore study;private final android.os.Handler logHandler=new android.os.Handler(android.os.Looper.getMainLooper());private String logItem="",learningSession=java.util.UUID.randomUUID().toString();private long lastTick=0,played=0;private boolean marked=false;
    private final Runnable record=new Runnable(){public void run(){recordPlayback();logHandler.postDelayed(this,250);}};
    private void recordPlayback(){long now=android.os.SystemClock.elapsedRealtime();if(player==null)return;if(audition){if(player.isPlaying()){auditionPlayed+=lastTick==0?0:Math.min(500,now-lastTick);if(!auditionHeard&&auditionPlayed>=250&&player.getCurrentMediaItem()!=null){study.heard(Catalog.id(player.getCurrentMediaItem().mediaId),learningSession,String.join(" + ",catalog.filters()));auditionHeard=true;}}lastTick=now;return;}MediaItem item=player.getCurrentMediaItem();String id=item==null?"":item.mediaId;if(!id.equals(logItem)){flushPlayback();logItem=id;played=0;marked=false;}if(player.isPlaying()&&!id.isEmpty()){long delta=lastTick==0?0:Math.min(500,now-lastTick);played+=delta;if(!marked&&played>=Math.min(1000,Math.max(250,player.getDuration()/2))){study.heard(Catalog.id(id),learningSession,String.join(" + ",catalog.filters()));marked=true;}if(played>=10000)flushPlayback();}lastTick=now;if(item!=null)catalog.prefs.edit().putLong("last_position",Math.max(0,player.getCurrentPosition())).apply();}
    private void flushPlayback(){if(played>0&&!logItem.isEmpty()&&study!=null){study.audio(Catalog.id(logItem),played,learningSession,String.join(" + ",catalog.filters()));played=0;}}
    private MediaSession session; private ExoPlayer player; private Catalog catalog;
    @Override public void onCreate(){
        super.onCreate();catalog=new Catalog(this);study=new StudyStore(this);
        // Short local clips need only a few seconds ahead; each MP4 period has its own sample index.
        var loadControl=new DefaultLoadControl.Builder().setBufferDurationsMs(2000,5000,250,500)
            .setTargetBufferBytes(512*1024).setPrioritizeTimeOverSizeThresholds(false).build();
        player=new ExoPlayer.Builder(this).setLoadControl(loadControl).setWakeMode(C.WAKE_MODE_LOCAL).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),true);
        player.setHandleAudioBecomingNoisy(true);
        player.addListener(new Player.Listener(){
            @Override public void onIsPlayingChanged(boolean playing){if(!audition){long now=System.currentTimeMillis();if(playing&&(lastListenActivity==0||now-lastListenActivity>5*60000L)){learningSession=java.util.UUID.randomUUID().toString();study.event(learningSession,"start",0,0,0,0,catalog.selected().size(),"듣고 익히기",0,String.join(" + ",catalog.filters()),learningSession);}lastListenActivity=now;}}
            @Override public void onMediaItemTransition(MediaItem item,int reason){
                if(item!=null&&!audition){catalog.prefs.edit().putString("last_id",item.mediaId).apply();Log.i("StudyPlayback","item="+item.mediaId+" playing="+player.getPlayWhenReady());}
            }
            @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_ENDED&&audition)flushAudition();}
            @Override public void onPlayerError(PlaybackException error){Log.e("StudyPlayback","Playback failed",error);}
        });
        var pending=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        session=new MediaSession.Builder(this,player).setSessionActivity(pending).setCallback(new MediaSession.Callback(){
            @Override public MediaSession.ConnectionResult onConnect(MediaSession s,MediaSession.ControllerInfo c){
                var base=MediaSession.Callback.super.onConnect(s,c);
                var commands=base.availableSessionCommands.buildUpon();
                if(getPackageName().equals(c.getPackageName()))commands.add(CONFIGURE).add(AUDITION).add(RESTORE);
                return new MediaSession.ConnectionResult.AcceptedResultBuilder(s).setAvailableSessionCommands(commands.build()).build();
            }
            @Override public ListenableFuture<SessionResult> onCustomCommand(MediaSession s,MediaSession.ControllerInfo c,SessionCommand cmd,Bundle args){
                if(getPackageName().equals(c.getPackageName())&&cmd.customAction.equals("audition")){audition(args);return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));}
                if(getPackageName().equals(c.getPackageName())&&cmd.customAction.equals("restore")){restoreListening();return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));}
                if(cmd.customAction.equals("configure")&&getPackageName().equals(c.getPackageName())){
                    audition=false;
                    configure(args);return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
                }
                return Futures.immediateFuture(new SessionResult(SessionError.ERROR_NOT_SUPPORTED));
            }
        }).build();
        Bundle restore=new Bundle();restore.putBoolean("restore",true);configure(restore);logHandler.post(record);
    }
    private void flushAudition(){if(auditionPlayed>0&&player.getCurrentMediaItem()!=null){study.audio(Catalog.id(player.getCurrentMediaItem().mediaId),auditionPlayed,learningSession,String.join(" + ",catalog.filters()));auditionPlayed=0;}}
    private void audition(Bundle args){
        if(audition)flushAudition();auditionHeard=false;
        if(!audition){recordPlayback();flushPlayback();savedId=player.getCurrentMediaItem()!=null?player.getCurrentMediaItem().mediaId:catalog.prefs.getString("last_id","1_0");savedPosition=player.getCurrentPosition();}
        audition=true;int id=args.getInt("id",1),v=Math.max(0,Math.min(3,args.getInt("variant",0)));var sentence=catalog.sentence(id);
        String title=args.getBoolean("masked",true)?"선별·학습 음성":sentence.id+". "+sentence.english;
        MediaItem clip=new MediaItem.Builder().setMediaId(id+"_"+v).setUri("asset:///full1000.m4a").setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder().setStartPositionMs(catalog.start(sentence,v)).setEndPositionMs(catalog.end(sentence,v)).build()).setMediaMetadata(new MediaMetadata.Builder().setTitle(title).setArtist("발화 "+(v+1)).build()).build();
        player.setMediaItem(clip);player.setRepeatMode(Player.REPEAT_MODE_OFF);player.setPlaybackSpeed(catalog.prefs.getFloat("speed",1f));player.prepare();player.play();
    }
    private void restoreListening(){if(!audition)return;flushAudition();audition=false;Bundle b=new Bundle();b.putInt("id",Catalog.id(savedId));b.putInt("variant",Catalog.variant(savedId));b.putLong("position",savedPosition);b.putBoolean("play",false);configure(b);}
    private void configure(Bundle args){
        boolean play=args.getBoolean("play",player.getPlayWhenReady());
        String current=player.getCurrentMediaItem()!=null?player.getCurrentMediaItem().mediaId:catalog.prefs.getString("last_id","1_0");
        int id=args.getInt("id",Catalog.id(current));int variant=args.getInt("variant",Catalog.variant(current));
        boolean first=catalog.prefs.getBoolean("first",false),single=catalog.prefs.getBoolean("single",false);
        boolean repeat=catalog.prefs.getBoolean("repeat",false);
        var selected=catalog.selected();
        if(selected.isEmpty()){player.pause();player.clearMediaItems();return;}
        boolean contains=false;for(var s:selected)if(s.id==id)contains=true;
        if(!contains){id=selected.get(0).id;variant=0;}
        List<MediaItem> queue=catalog.queue(first,single?id:0);
        String target=id+"_"+(first?0:variant);int index=0;
        for(int n=0;n<queue.size();n++)if(queue.get(n).mediaId.equals(target)){index=n;break;}
        flushPlayback();player.setMediaItems(queue,index,args.containsKey("position")?args.getLong("position"):args.getBoolean("restore",false)?catalog.prefs.getLong("last_position",0):0);player.setRepeatMode(single||repeat?Player.REPEAT_MODE_ALL:Player.REPEAT_MODE_OFF);
        player.setPlaybackSpeed(catalog.prefs.getFloat("speed",1f));player.prepare();player.setPlayWhenReady(play);
        Log.i("StudyPlayback","queue="+queue.size()+" first="+first+" single="+single+" repeat="+repeat);
    }
    @Override public MediaSession onGetSession(MediaSession.ControllerInfo info){return session;}
    @Override public void onDestroy(){logHandler.removeCallbacks(record);flushPlayback();flushAudition();if(study!=null)study.close();if(session!=null){session.release();session=null;}if(player!=null){player.release();player=null;}super.onDestroy();}
}
