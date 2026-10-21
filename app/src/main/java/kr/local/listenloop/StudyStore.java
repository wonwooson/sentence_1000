package kr.local.listenloop;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

/** Independent schema: never rewrites the original study preferences. */
public final class StudyStore extends SQLiteOpenHelper {
    private final SharedPreferences prefs;
    public StudyStore(Context c){this(c,"learning");}
    StudyStore(Context c,String name){super(c,name+".db",null,1);prefs=c.getSharedPreferences(name,0);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE rating(id INTEGER, criterion INTEGER, value INTEGER, time INTEGER, PRIMARY KEY(id,criterion))");
        db.execSQL("CREATE TABLE event(key TEXT PRIMARY KEY,type TEXT,id INTEGER,criterion INTEGER,before_value INTEGER,value INTEGER,time INTEGER,amount INTEGER,mode TEXT,hint INTEGER,scope TEXT,session TEXT)");
        db.execSQL("CREATE INDEX event_time ON event(time)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next){}
    public int rating(int id,int criterion){try(Cursor c=getReadableDatabase().rawQuery("SELECT value FROM rating WHERE id=? AND criterion=?",new String[]{""+id,""+criterion})){return c.moveToFirst()?c.getInt(0):0;}}
    public long ratedAt(int id,int criterion){try(Cursor c=getReadableDatabase().rawQuery("SELECT time FROM rating WHERE id=? AND criterion=?",new String[]{""+id,""+criterion})){return c.moveToFirst()?c.getLong(0):0;}}
    public void rate(String key,int id,int criterion,int value,String scope,String session){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            int before=rating(id,criterion);try(Cursor c=db.rawQuery("SELECT before_value FROM event WHERE key=?",new String[]{key})){if(c.moveToFirst())before=c.getInt(0);}
            ContentValues r=new ContentValues();r.put("id",id);r.put("criterion",criterion);r.put("value",value);r.put("time",System.currentTimeMillis());db.insertWithOnConflict("rating",null,r,SQLiteDatabase.CONFLICT_REPLACE);
            event(key,"rating",id,criterion,before,value,0,"selection",0,scope,session);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void event(String key,String type,int id,int criterion,int before,int value,long amount,String mode,int hint,String scope,String session){
        ContentValues v=new ContentValues();v.put("key",key);v.put("type",type);v.put("id",id);v.put("criterion",criterion);v.put("before_value",before);v.put("value",value);v.put("time",System.currentTimeMillis());v.put("amount",amount);v.put("mode",mode);v.put("hint",hint);v.put("scope",scope);v.put("session",session);getWritableDatabase().insertWithOnConflict("event",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public void audio(int id,long ms,String session,String scope){event(UUID.randomUUID().toString(),"audio",id,0,0,0,ms,"listen",0,scope,session);}
    public void heard(int id,String session,String scope){event("heard_"+id,"heard",id,0,0,0,0,"listen",0,scope,session);}
    public Set<Integer> heardIds(){Set<Integer> r=new HashSet<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM event WHERE type='heard'",null)){while(c.moveToNext())r.add(c.getInt(0));}return r;}
    public int[] counts(List<Catalog.Sentence> list,int criterion){int[] n=new int[4];for(var s:list)n[rating(s.id,criterion)]++;return n;}
    public List<Integer> candidates(List<Catalog.Sentence> list,int criterion,int target){List<Integer> r=new ArrayList<>();Map<Integer,Long> recent=new HashMap<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,MAX(time) FROM event WHERE criterion=? AND type IN ('answer','rating') GROUP BY id",new String[]{""+criterion})){while(c.moveToNext())recent.put(c.getInt(0),c.getLong(1));}for(var s:list){int v=rating(s.id,criterion);if(target==2||target==1&&v==0||target==0&&(v==1||v==2))r.add(s.id);}r.sort(Comparator.comparingInt((Integer id)->{int v=rating(id,criterion);return v==1?0:v==2?1:v==0?2:3;}).thenComparingLong(id->recent.getOrDefault(id,0L)));return r;}
    public JSONObject task(String key){try{return new JSONObject(prefs.getString(key,"{}"));}catch(Exception e){return new JSONObject();}}
    public void task(String key,JSONObject value){prefs.edit().putString(key,value.toString()).apply();}
    public int criterion(){return prefs.getInt("criterion",0);}
    public void criterion(int c){prefs.edit().putInt("criterion",c).apply();}
    public int gains(List<Catalog.Sentence> list,int criterion){Set<Integer> ids=new HashSet<>(),g=new HashSet<>();for(var s:list)ids.add(s.id);try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM event WHERE type='rating' AND criterion=? AND before_value IN (1,2) AND value=3 AND time>=?",new String[]{""+criterion,""+(System.currentTimeMillis()-7L*86400000)})){while(c.moveToNext())if(ids.contains(c.getInt(0))&&rating(c.getInt(0),criterion)==3)g.add(c.getInt(0));}return g.size();}
    public String analysis(List<Catalog.Sentence> list,int criterion){
        Set<Integer> ids=new HashSet<>();for(var s:list)ids.add(s.id);long audio=0;int answers=0,hints=0,retained=0,rechecked=0;long[] ms=new long[4];int[] answered=new int[4];Map<Integer,Long> lastGood=new HashMap<>();Map<Integer,Long> lastPractice=new HashMap<>();int[] reviews=new int[4],good=new int[4];
        String[] bands={"새벽 0–6시","오전 6–12시","오후 12–18시","저녁 18–24시"};
        try(Cursor c=getReadableDatabase().rawQuery("SELECT type,id,criterion,value,time,amount,hint FROM event ORDER BY time",null)){while(c.moveToNext()){
            int id=c.getInt(1);if(!ids.contains(id))continue;String type=c.getString(0);long t=c.getLong(4);Calendar cal=Calendar.getInstance();cal.setTimeInMillis(t);int band=cal.get(Calendar.HOUR_OF_DAY)/6;
            if(type.equals("audio")){audio+=c.getLong(5);ms[band]+=c.getLong(5);lastPractice.put(id,t);}
            if(type.equals("answer")){answers++;answered[band]++;if(c.getInt(6)>0)hints++;lastPractice.put(id,t);}
            if(type.equals("rating")&&c.getInt(2)==criterion){int v=c.getInt(3);Long prev=lastGood.get(id);if(prev!=null&&t-prev>=86400000L){rechecked++;if(v==3)retained++;}if(v==3)lastGood.put(id,t);else lastGood.remove(id);
                Long practice=lastPractice.get(id);if(practice!=null){long gap=t-practice;int b=gap<3600000?0:gap<86400000?1:gap<3*86400000L?2:3;reviews[b]++;if(v==3)good[b]++;}}
        }}
        StringBuilder s=new StringBuilder("실제 재생 "+audio/60000+"분 · 답 확인 "+answers+"회\n힌트 사용 "+hints+"회\n\n하루 이상 지나 재확인\n나옴 유지 "+retained+" / "+rechecked+"회\n\n학습 시각별 기록\n");for(int i=0;i<4;i++)s.append(bands[i]+"  재생 "+ms[i]/60000+"분 · 답 확인 "+answered[i]+"회\n");
        s.append("\n마지막 학습 후 선별까지의 간격\n");String[] gaps={"1시간 이내","1시간–1일","1–3일","3일 이상"};for(int i=0;i<4;i++)s.append(gaps[i]+"  나옴 "+good[i]+" / "+reviews[i]+"회\n");
        s.append("\n최근 학습 시작 기록 · 전체 범위\n");
        ArrayList<Long> starts=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT time,mode,amount,scope FROM event WHERE type='start' ORDER BY time DESC LIMIT 6",null)){while(c.moveToNext()){starts.add(c.getLong(0));labels.add(c.getString(1)+" · "+c.getLong(2)+"문장\n"+c.getString(3));}}
        java.text.SimpleDateFormat fmt=new java.text.SimpleDateFormat("M/d HH:mm",Locale.getDefault());
        for(int i=0;i<Math.min(5,starts.size());i++){s.append(fmt.format(new Date(starts.get(i)))+"  "+labels.get(i)+"\n");if(i+1<starts.size())s.append("이전 시작과 간격 "+(starts.get(i)-starts.get(i+1))/60000+"분\n");}
        s.append("\n아직 어떤 조건이 더 효과적인지 단정하지 않습니다. 위 수치는 관찰 기록이며 문장 난이도·힌트·연습 직후 효과가 섞여 있습니다. 재생 시간은 집중해서 들었다는 뜻이 아닙니다.");return s.toString();
    }
    public static void put(JSONObject o,String k,Object v){try{o.put(k,v);}catch(JSONException e){throw new IllegalStateException(e);}}
    public static String normalize(String s){return s.toLowerCase(Locale.ROOT).replace('’','\'').replaceAll("[^\\p{L}\\p{N}\\s']"," ").replaceAll("\\s+"," ").trim();}
}
