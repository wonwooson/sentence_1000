package kr.local.listenloop;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import androidx.media3.common.*;

public final class Catalog {
    public static final long SOURCE_MS = 27450000;
    public static final String PREFS = "study";
    public final List<Sentence> sentences = new ArrayList<>();
    public final Context context;
    public final SharedPreferences prefs;
    public static class Sentence {
        public int id; public String english, korean, note, explanation;
        public List<String> tags=new ArrayList<>(); public boolean commonSound;
        public String[] sounds = new String[4];
        public long[] starts = new long[4], ends = new long[4];
    }
    public Catalog(Context context) {
        this.context=context; prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        try (var in=context.getAssets().open("catalog.json")) {
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)bytes.write(buffer,0,count);
            JSONArray data=new JSONArray(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
            for(int i=0;i<data.length();i++) {
                JSONObject o=data.getJSONObject(i); Sentence s=new Sentence();
                s.id=o.getInt("id");s.english=o.getString("english");s.korean=o.getString("korean");s.note=o.optString("note","");s.explanation=o.optString("explanation","");
                JSONArray sounds=o.getJSONArray("pronunciations");s.commonSound=sounds.length()==1;
                JSONArray tags=o.optJSONArray("tags");if(tags!=null)for(int t=0;t<tags.length();t++)s.tags.add(tags.getString(t));
                for(int v=0;v<4;v++){s.sounds[v]=sounds.getString(s.commonSound?0:v);JSONObject c=o.getJSONArray("clips").getJSONObject(v);s.starts[v]=c.getLong("start_ms");s.ends[v]=c.getLong("end_ms");}
                sentences.add(s);
            }
        } catch(Exception e){throw new IllegalStateException("학습 자료를 읽을 수 없습니다",e);}
    }
    public long start(Sentence s,int v){return prefs.getLong("start_"+s.id+"_"+v,s.starts[v]);}
    public long end(Sentence s,int v){return prefs.getLong("end_"+s.id+"_"+v,s.ends[v]);}
    public Sentence sentence(int id){return sentences.get(Math.max(1,Math.min(sentences.size(),id))-1);}
    public long duration(){return sentences.get(sentences.size()-1).ends[3];}
    public Set<String> filters(){return new HashSet<>(prefs.getStringSet("filters",Collections.emptySet()));}
    public List<Sentence> selected(){return select(filters(),prefs.getInt("studyFilter",0));}
    public List<Sentence> select(Set<String> filters,int study){
        List<Sentence> result=new ArrayList<>();
        for(Sentence s:sentences){if(!s.tags.containsAll(filters))continue;
            if(study==1&&!prefs.getBoolean("star_"+s.id,false))continue;
            if(study>=2&&prefs.getInt("level_"+s.id,0)!=study-2)continue;
            result.add(s);}
        return result;
    }
    public Map<String,Integer> categories(){
        List<String> order=Arrays.asList("추천","스피킹","동의","반대","리스닝","문법","시간","형태","극성","연습");
        Map<String,Integer> result=new TreeMap<>((a,b)->{int x=order.indexOf(a.split(" · ")[0]),y=order.indexOf(b.split(" · ")[0]);return x==y?a.compareTo(b):Integer.compare(x,y);});
        for(Sentence s:sentences)for(String t:s.tags)result.put(t,result.getOrDefault(t,0)+1);return result;
    }
    public static int id(String mediaId){try{return Integer.parseInt(mediaId.split("_")[0]);}catch(Exception e){return 1;}}
    public static int variant(String mediaId){try{return Integer.parseInt(mediaId.split("_")[1]);}catch(Exception e){return 0;}}
    public List<MediaItem> queue(boolean firstOnly,int onlySentence) {
        List<MediaItem> items=new ArrayList<>();
        for(Sentence s:selected()) {
            if(onlySentence>0&&s.id!=onlySentence)continue;
            for(int v=0;v<(firstOnly?1:4);v++) {
                items.add(new MediaItem.Builder().setMediaId(s.id+"_"+v).setUri("asset:///full1000.m4a")
                  .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder().setStartPositionMs(start(s,v)).setEndPositionMs(end(s,v)).build())
                  .setMediaMetadata(new MediaMetadata.Builder().setTitle(s.id+". "+s.english).setArtist("발화 "+(v+1)+"  "+s.sounds[v]).setAlbumTitle("뒹굴신 입영작 1000문장").build()).build());
            }
        }
        return items;
    }
}
