package kr.local.listenloop;
import android.app.Instrumentation;
import android.os.Bundle;
import android.content.Context;
import java.util.*;
import org.json.*;
public class StudyInstrumentation extends Instrumentation {
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 private void check(boolean ok,String m){if(!ok)throw new AssertionError(m);}
 @Override public void onStart(){Bundle out=new Bundle();try{
   // A separate test-only name isolates all data from real learning records.
   Context c=getTargetContext();c.deleteDatabase("learning_test.db");StudyStore s=new StudyStore(c,"learning_test");
   check(s.rating(1,0)==0,"new is unclassified");s.rate("a",1,0,1,"scope","test");s.rate("b",1,1,3,"scope","test");
   check(s.rating(1,0)==1&&s.rating(1,1)==3,"criteria independent");
   List<Catalog.Sentence> list=new ArrayList<>();for(int i=1;i<=4;i++){Catalog.Sentence x=new Catalog.Sentence();x.id=i;list.add(x);}
   s.rate("c",2,0,2,"scope","test");check(s.candidates(list,0,0).equals(Arrays.asList(1,2)),"difficult priority");
   check(s.candidates(list,0,1).equals(Arrays.asList(3,4)),"unclassified separate");
   s.rate("rev",1,0,3,"scope","test");check(s.gains(list,0)==1,"gain");s.rate("rev",1,0,2,"scope","test");check(s.gains(list,0)==0,"correction removes gain");
   s.event("answer","answer",2,0,0,1,1000,"dictation",0,"scope","test");check(s.rating(2,0)==2,"answer does not change rating");
   JSONObject t=new JSONObject();t.put("ids",new JSONArray(Arrays.asList(1,2)));t.put("index",1);s.task("batch",t);s.close();s=new StudyStore(c,"learning_test");check(s.task("batch").getInt("index")==1,"task persists");
   check(StudyStore.normalize("Let's start fresh!").equals(StudyStore.normalize("LET’S start fresh.")),"punctuation comparison");
   check(!StudyStore.normalize("I don't know").equals(StudyStore.normalize("I do know")),"negation not erased");
   s.heard(1,"test","scope");s.heard(1,"test","scope");check(s.heardIds().size()==1,"unique heard");s.close();out.putString("stream","PASS: 10 study invariants\n");finish(-1,out);
 }catch(Throwable e){out.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(0,out);}}
}
