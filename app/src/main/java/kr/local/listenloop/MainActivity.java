package kr.local.listenloop;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import androidx.core.content.ContextCompat;
import androidx.media3.common.*;
import androidx.media3.session.*;
import androidx.media3.session.MediaController;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Locale;
import java.util.*;

@androidx.media3.common.util.UnstableApi
public class MainActivity extends Activity {
    private final int INK=Color.rgb(23,52,84),BLUE=Color.rgb(33,89,214),MUTED=Color.rgb(86,108,132),BG=Color.rgb(243,247,252),PALE=Color.rgb(225,235,254);
    private Catalog catalog; private MediaController controller; private ListenableFuture<MediaController> connection;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView heading,english,korean,phonetic,status,clock,note,selection,tagText; private Button play,all,first,speed,single,repeat,star,level;
    private ProgressBar progress;private LinearLayout soundList;private Button[] soundButtons=new Button[4];
    private String refreshStamp="",shown="";private int currentId=1,currentVariant=0;
    private final Player.Listener listener=new Player.Listener(){
        @Override public void onEvents(Player p,Player.Events events){refresh();}
        @Override public void onPlayerError(PlaybackException e){status.setText("재생 오류: "+e.getErrorCodeName()+" · 재생 버튼으로 다시 시도");}
    };
    private final Runnable tick=new Runnable(){public void run(){refresh();handler.postDelayed(this,100);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);catalog=new Catalog(this);buildUi();if(getIntent().getBooleanExtra("settings",false))showSettings();
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextColor(color);v.setTextSize(size);v.setPadding(0,dp(5),0,dp(5));v.setFontFeatureSettings("kern");return v;}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private Button button(String title,Runnable action){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(INK);b.setMinHeight(dp(48));b.setPadding(dp(8),dp(5),dp(8),dp(5));b.setBackground(bg(Color.WHITE,12));b.setOnClickListener(v->action.run());return b;}
    private void weighted(LinearLayout row,View view){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(50),1);p.setMargins(dp(3),dp(4),dp(3),dp(4));row.addView(view,p);}
    private void gap(LinearLayout l,int h){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private void buildUi(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout outer=vertical();outer.setBackgroundColor(BG);outer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout root=vertical();root.setPadding(dp(20),dp(12),dp(20),dp(24));scroll.addView(root);setContentView(outer);
        outer.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout header=horizontal();TextView title=text("듣고 익히기",23,INK);title.setTypeface(null,Typeface.BOLD);
        header.addView(button("‹ 홈",()->{if(isTaskRoot())startActivity(new Intent(this,LearningActivity.class));finish();}),new LinearLayout.LayoutParams(dp(65),dp(48)));header.addView(title,new LinearLayout.LayoutParams(0,-2,1));header.addView(button("설정",this::showSettings),new LinearLayout.LayoutParams(dp(72),dp(48)));root.addView(header);gap(root,12);
        LinearLayout sheet=vertical();sheet.setPadding(dp(18),dp(14),dp(18),dp(16));sheet.setBackground(bg(Color.WHITE,20));root.addView(sheet);
        heading=text("1 / 1000",14,MUTED);sheet.addView(heading);
        english=text("Let's start fresh.",23,INK);english.setTypeface(null,Typeface.BOLD);sheet.addView(english);
        korean=text("새로 시작하자.",16,MUTED);sheet.addView(korean);gap(sheet,8);
        phonetic=text("레ㅆ따r fㅍr뤠쉬",30,BLUE);phonetic.setTypeface(null,Typeface.BOLD);phonetic.setLineSpacing(dp(4),1f);sheet.addView(phonetic);
        soundList=vertical();
        for(int i=0;i<4;i++){final int v=i;Button b=button("발화 "+(i+1),()->chooseVariant(v));b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);soundButtons[i]=b;LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(3),0,dp(3));soundList.addView(b,lp);}
        sheet.addView(soundList);
        note=text("",12,MUTED);sheet.addView(note);
        tagText=text("",12,MUTED);sheet.addView(tagText);tagText.setOnClickListener(v->showTags());
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);sheet.addView(progress,new LinearLayout.LayoutParams(-1,dp(6)));
        clock=text("",12,MUTED);sheet.addView(clock);
        LinearLayout transport=horizontal();transport.setPadding(dp(16),dp(3),dp(16),dp(3));weighted(transport,button("이전 문장",()->jump(-1)));play=button("재생",this::togglePlay);weighted(transport,play);weighted(transport,button("다음 문장",()->jump(1)));outer.addView(transport);
        status=text("재생기를 준비하고 있어요",13,MUTED);root.addView(status);
        LinearLayout options=horizontal();single=button("한 문장 반복",()->toggle("single"));repeat=button("전체 반복",()->toggle("repeat"));weighted(options,single);weighted(options,repeat);root.addView(options);
        LinearLayout tools=horizontal();weighted(tools,button("문장 고르기",this::pickSentence));star=button("즐겨찾기",this::toggleStar);weighted(tools,star);root.addView(tools);
        buildSettings();
        updateSettings();renderSentence(1,0);
    }
    private LinearLayout settingsContent;
    private void buildSettings(){
        settingsContent=vertical();settingsContent.setPadding(dp(16),dp(8),dp(16),dp(12));
        selection=text("",13,MUTED);settingsContent.addView(selection);
        LinearLayout filters=horizontal();weighted(filters,button("학습 목록",this::pickStudy));settingsContent.addView(filters);
        LinearLayout modes=horizontal();all=button("네 발화 모두",()->mode(false));first=button("첫 발화만",()->mode(true));weighted(modes,all);weighted(modes,first);settingsContent.addView(modes);
        LinearLayout playback=horizontal();speed=button("1.0×",this::changeSpeed);weighted(playback,speed);weighted(playback,button("싱크 조정",this::editSync));settingsContent.addView(playback);
        level=button("기존 학습 상태",()->{});
        gap(settingsContent,8);settingsContent.addView(button("분류와 발음 표기 안내",this::about),new LinearLayout.LayoutParams(-1,dp(50)));
        settingsContent.addView(text("첫 발화만 모드에서도 화면을 끄고 들을 수 있어요.\n선택한 카테고리의 문장끼리 이어집니다.\n발화별 싱크 조정은 다른 문장에 영향을 주지 않아요.",12,MUTED));
    }
    private void showSettings(){
        if(settingsContent.getParent()!=null)((android.view.ViewGroup)settingsContent.getParent()).removeView(settingsContent);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);scroll.addView(settingsContent);updateSettings();
        AlertDialog settingsDialog=new AlertDialog.Builder(this).setTitle("설정").setView(scroll).setPositiveButton("닫기",null).create();settingsDialog.setOnDismissListener(d->{if(getIntent().getBooleanExtra("settings",false))finish();});settingsDialog.show();
    }
    @Override protected void onStart(){super.onStart();
        SessionToken token=new SessionToken(this,new ComponentName(this,PlaybackService.class));
        final ListenableFuture<MediaController> future=new MediaController.Builder(this,token).buildAsync();connection=future;
        future.addListener(()->{if(connection!=future)return;try{controller=future.get();controller.sendCustomCommand(PlaybackService.RESTORE,Bundle.EMPTY);controller.addListener(listener);if(catalog.prefs.getBoolean("scopeDirty",false)){catalog.prefs.edit().putBoolean("scopeDirty",false).apply();configure(Catalog.id(catalog.prefs.getString("last_id","1_0")),0,false);}handler.post(tick);}catch(Exception e){status.setText("재생기를 연결하지 못했습니다. 앱을 다시 열어주세요.");}},ContextCompat.getMainExecutor(this));
    }
    @Override protected void onStop(){handler.removeCallbacks(tick);if(controller!=null)controller.removeListener(listener);controller=null;if(connection!=null){MediaController.releaseFuture(connection);connection=null;}super.onStop();}
    private boolean ready(){if(controller==null){Toast.makeText(this,"재생기를 연결 중입니다",Toast.LENGTH_SHORT).show();return false;}return true;}
    private void configure(int id,int variant,boolean start){if(!ready())return;Bundle args=new Bundle();args.putInt("id",id);args.putInt("variant",variant);args.putBoolean("play",start);controller.sendCustomCommand(PlaybackService.CONFIGURE,args);updateSettings();}
    private void mode(boolean firstOnly){if(!ready())return;catalog.prefs.edit().putBoolean("first",firstOnly).apply();configure(currentId,firstOnly?0:currentVariant,controller.getPlayWhenReady());}
    private void chooseVariant(int v){if(!ready())return;if(v>0)catalog.prefs.edit().putBoolean("first",false).apply();configure(currentId,v,true);}
    private void jump(int delta){if(!ready())return;boolean keepPlaying=controller.getPlayWhenReady()&&controller.getPlaybackState()!=Player.STATE_ENDED;var list=catalog.selected();if(list.isEmpty()){Toast.makeText(this,"학습 조건에 맞는 문장이 없어요. 조건을 바꿔주세요.",Toast.LENGTH_LONG).show();return;}int at=0;for(int n=0;n<list.size();n++)if(list.get(n).id==currentId)at=n;configure(list.get(Math.max(0,Math.min(list.size()-1,at+delta))).id,0,keepPlaying);}
    private void togglePlay(){if(!ready())return;if(controller.getPlaybackState()==Player.STATE_ENDED){controller.seekTo(0,0);controller.play();}else if(controller.isPlaying()||controller.getPlayWhenReady())controller.pause();else{if(controller.getPlaybackState()==Player.STATE_IDLE)controller.prepare();controller.play();}}
    private void toggle(String key){if(!ready())return;boolean value=!catalog.prefs.getBoolean(key,false);var edit=catalog.prefs.edit().putBoolean(key,value);if(value)edit.putBoolean(key.equals("single")?"repeat":"single",false);edit.apply();configure(currentId,currentVariant,controller.getPlayWhenReady());}
    private void changeSpeed(){new AlertDialog.Builder(this).setTitle("재생 속도").setItems(new String[]{"0.75×","1.0×","1.25×"},(d,n)->{float value=new float[]{.75f,1f,1.25f}[n];catalog.prefs.edit().putFloat("speed",value).apply();if(controller!=null)controller.setPlaybackSpeed(value);updateSettings();}).show();}
    private void updateSettings(){
        boolean f=catalog.prefs.getBoolean("first",false);style(all,!f);style(first,f);style(single,catalog.prefs.getBoolean("single",false));style(repeat,catalog.prefs.getBoolean("repeat",false));
        speed.setText(Float.toString(catalog.prefs.getFloat("speed",1f))+"×");style(play,true);
        String filter=catalog.filters().isEmpty()?"전체 문장":String.join(" + ",catalog.filters());
        selection.setText(filter+" · "+studyNames[catalog.prefs.getInt("studyFilter",0)]+" · "+catalog.selected().size()+"문장");
        updateLearning();
    }
    private void style(Button b,boolean active){b.setBackground(bg(active?BLUE:Color.WHITE,12));b.setTextColor(active?Color.WHITE:INK);}
    private void renderSentence(int id,int variant){
        currentId=id;currentVariant=variant;Catalog.Sentence s=catalog.sentence(id);heading.setText(String.format(Locale.US,"%d / 1000   ·   발화 %d / 4",id,variant+1));english.setText(s.english);korean.setText(s.korean);phonetic.setText(s.sounds[variant]);note.setText(s.explanation);note.setVisibility(s.explanation.isEmpty()?View.GONE:View.VISIBLE);
        tagText.setText(String.join("  ·  ",s.tags.subList(0,Math.min(3,s.tags.size())))+"  ·  분류 모두 보기 ›");updateLearning();
        for(int v=0;v<4;v++){Button b=soundButtons[v];b.setText((v+1)+"   "+(s.commonSound?"발화 "+(v+1)+" 듣기 · PDF 공통 표기":s.sounds[v]));b.setTextColor(v==variant?BLUE:MUTED);b.setBackground(bg(v==variant?PALE:Color.WHITE,10));b.setTypeface(null,v==variant?Typeface.BOLD:Typeface.NORMAL);b.setContentDescription("발화 "+(v+1)+" "+s.sounds[v]);}
    }
    private void refresh(){
        if(controller==null)return;MediaItem item=controller.getCurrentMediaItem();String stamp=(item==null?"":item.mediaId)+":"+controller.getCurrentPosition()+":"+controller.getPlayWhenReady()+":"+controller.getPlaybackState()+":"+controller.getPlayerError();if(stamp.equals(refreshStamp))return;refreshStamp=stamp;if(item==null){status.setText("학습 조건에 맞는 문장이 없어요. 카테고리나 복습 목록을 바꿔주세요.");play.setText("재생");return;}
        if(!item.mediaId.equals(shown)){shown=item.mediaId;renderSentence(Catalog.id(shown),Catalog.variant(shown));}
        long pos=Math.max(0,controller.getCurrentPosition()),dur=controller.getDuration();
        progress.setProgress(dur>0?(int)Math.min(1000,pos*1000/dur):0);
        Catalog.Sentence s=catalog.sentence(currentId);long start=catalog.start(s,currentVariant),end=catalog.end(s,currentVariant);
        clock.setText(String.format(Locale.US,"%.2f / %.2f초   ·   원본 %s",pos/1000.0,(end-start)/1000.0,time(Catalog.SOURCE_MS+start+pos)));
        play.setText(controller.getPlaybackState()==Player.STATE_ENDED?"처음부터":controller.getPlayWhenReady()?"일시정지":"재생");
        if(controller.getPlayerError()!=null)status.setText("재생 오류 · 재생 버튼으로 다시 시도");
        else if(controller.getPlaybackState()==Player.STATE_ENDED)status.setText("학습을 마쳤어요. 재생을 누르면 처음부터 들어요.");
        else if(controller.getPlaybackState()==Player.STATE_BUFFERING)status.setText("음성을 준비하고 있어요");
        else status.setText(controller.isPlaying()?"화면을 꺼도 계속 재생됩니다":"준비됐어요 · 발음 표기와 함께 들어보세요");
    }
    private String time(long ms){long sec=ms/1000;return String.format(Locale.US,"%d:%02d:%02d",sec/3600,sec/60%60,sec%60);}
    private void pickSentence(){
        LinearLayout form=vertical();form.setPadding(dp(16),0,dp(16),0);EditText search=new EditText(this);search.setSingleLine(true);search.setHint("번호·영어·뜻 검색");form.addView(search);
        TextView count=text("",13,MUTED);form.addView(count);ListView list=new ListView(this);form.addView(list,new LinearLayout.LayoutParams(-1,dp(320)));
        ArrayList<Catalog.Sentence> matches=new ArrayList<>();ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,new ArrayList<>());list.setAdapter(adapter);
        Runnable fill=()->{String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);matches.clear();adapter.setNotifyOnChange(false);adapter.clear();for(var s:catalog.selected())if(q.isEmpty()||String.valueOf(s.id).equals(q)||s.english.toLowerCase(Locale.ROOT).contains(q)||s.korean.contains(q)){matches.add(s);adapter.add(s.id+". "+s.english+"\n"+s.korean);}adapter.notifyDataSetChanged();count.setText("현재 학습 조건에서 "+matches.size()+"문장");};
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){fill.run();}public void afterTextChanged(Editable e){}});fill.run();
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("문장 고르기").setView(form).setNegativeButton("닫기",null).create();list.setOnItemClickListener((p,v,n,id)->{configure(matches.get(n).id,0,true);dialog.dismiss();});dialog.show();
    }
    private final String[] studyNames={"모든 상태","즐겨찾기","이전 기록 · 아직 연습 전","이전 기록 · 아직 안 들림","이전 기록 · 들리지만 못 말함","이전 기록 · 혼자 말할 수 있음","이전 기록 · 다른 화자도 이해"};
    private final String[] levelNames={"아직 연습 전","아직 안 들림","들리지만 못 말함","혼자 말할 수 있음","다른 화자도 이해"};
    private void updateLearning(){if(star==null||level==null)return;boolean value=catalog.prefs.getBoolean("star_"+currentId,false);star.setText(value?"★ 즐겨찾기":"☆ 즐겨찾기");style(star,value);level.setText(levelNames[catalog.prefs.getInt("level_"+currentId,0)]);}
    private void toggleStar(){catalog.prefs.edit().putBoolean("star_"+currentId,!catalog.prefs.getBoolean("star_"+currentId,false)).apply();updateSettings();if(catalog.prefs.getInt("studyFilter",0)==1)configure(currentId,0,false);}
    private void pickLevel(){new AlertDialog.Builder(this).setTitle("이 문장의 학습 상태").setSingleChoiceItems(levelNames,catalog.prefs.getInt("level_"+currentId,0),(d,n)->{catalog.prefs.edit().putInt("level_"+currentId,n).apply();updateSettings();if(catalog.prefs.getInt("studyFilter",0)>=2)configure(currentId,0,false);d.dismiss();}).setNegativeButton("닫기",null).show();}
    private void pickStudy(){new AlertDialog.Builder(this).setTitle("학습 목록").setSingleChoiceItems(studyNames,catalog.prefs.getInt("studyFilter",0),(d,n)->{if(catalog.select(catalog.filters(),n).isEmpty()){Toast.makeText(this,"해당 문장이 없어요. 먼저 즐겨찾기나 학습 상태를 표시해 주세요.",Toast.LENGTH_LONG).show();return;}catalog.prefs.edit().putInt("studyFilter",n).apply();configure(currentId,0,false);d.dismiss();}).setNegativeButton("닫기",null).show();}
    private void pickCategories(){
        var counts=catalog.categories();String[] keys=counts.keySet().toArray(new String[0]),labels=new String[keys.length];boolean[] checked=new boolean[keys.length];Set<String> chosen=catalog.filters();for(int n=0;n<keys.length;n++){labels[n]=keys[n]+" ("+counts.get(keys[n])+")";checked[n]=chosen.contains(keys[n]);}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("함께 연습할 조건 · 모두 만족").setMultiChoiceItems(labels,checked,(d,n,b)->{if(b)chosen.add(keys[n]);else chosen.remove(keys[n]);((AlertDialog)d).setTitle("조건에 맞는 문장 "+catalog.select(chosen,catalog.prefs.getInt("studyFilter",0)).size()+"개");}).setPositiveButton("적용",null).setNeutralButton("전체 문장",null).setNegativeButton("취소",null).create();
        dialog.setOnShowListener(d->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(catalog.select(chosen,catalog.prefs.getInt("studyFilter",0)).isEmpty()){Toast.makeText(this,"조건을 줄여주세요. 두 조건은 모두 만족해야 합니다.",Toast.LENGTH_LONG).show();return;}catalog.prefs.edit().putStringSet("filters",chosen).apply();configure(currentId,0,false);dialog.dismiss();});dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{catalog.prefs.edit().remove("filters").putInt("studyFilter",0).apply();configure(currentId,0,false);dialog.dismiss();});});dialog.show();
    }
    private void showTags(){new AlertDialog.Builder(this).setTitle(currentId+"번 학습 분류").setMessage(String.join("\n",catalog.sentence(currentId).tags)).setPositiveButton("닫기",null).show();}
    private void about(){new AlertDialog.Builder(this).setTitle("학습 분류와 발음 표기").setMessage("문법적 긍정·부정과 대화에서의 동의·반대는 다른 기준입니다. 여러 조건을 고르면 모두 만족하는 문장만 재생합니다.\n\n분류는 PDF 문장과 뜻에 근거한 학습용 묶음입니다. 장면의 반어·감정·공손함은 배우마다 다를 수 있습니다. 원어민 사용 빈도 순위는 측정하지 않았습니다.\n\n리스닝 묶음은 소리에 주목할 구간을 추천합니다. 모든 배우에게 같은 연음이 나타나거나 난이도가 같다는 뜻은 아닙니다. -ing 형태에는 진행형뿐 아니라 동명사·형용사도 포함됩니다.\n\n1–500번은 PDF의 네 줄 발음 표기, 501–1000번은 공통 한 줄 표기를 보존했습니다. 강세 표식은 추가하지 않았습니다.\n\n영상의 번호 전환으로 발화별 시간을 저장했습니다. 1–20번은 실제 휴대폰에서 사용자 검증을 마쳤고, 전체 발화를 끝까지 듣는 청취 검수는 아직 완료하지 않았습니다.").setPositiveButton("닫기",null).show();}
    private void editSync(){
        if(!ready())return;final int id=currentId,v=currentVariant;Catalog.Sentence s=catalog.sentence(id);
        controller.pause();LinearLayout form=vertical();form.setPadding(dp(20),dp(8),dp(20),dp(8));
        form.addView(text(s.english+"\n발화 "+(v+1)+" · 음원 시작 기준 초",15,INK));
        EditText start=field(form,"시작",catalog.start(s,v)),end=field(form,"끝",catalog.end(s,v));
        form.addView(text("0.05초씩 조정할 수 있어요. 저장 후 이 발화부터 다시 듣습니다.",13,MUTED));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("싱크 조정").setView(form).setPositiveButton("저장 후 듣기",null).setNeutralButton("원래 값",null).setNegativeButton("취소",null).create();
        dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{try{
                long a=Math.round(Double.parseDouble(start.getText().toString())*1000),b=Math.round(Double.parseDouble(end.getText().toString())*1000);
                if(a<0||b>catalog.duration()||b-a<150)throw new IllegalArgumentException();
                catalog.prefs.edit().putLong("start_"+id+"_"+v,a).putLong("end_"+id+"_"+v,b).apply();shown="";configure(id,v,true);dialog.dismiss();
            }catch(Exception e){Toast.makeText(this,"음원 범위 안에서 시작보다 끝을 0.15초 이상 뒤로 설정해 주세요",Toast.LENGTH_LONG).show();}});
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(w->{start.setText(String.format(Locale.US,"%.3f",s.starts[v]/1000.0));end.setText(String.format(Locale.US,"%.3f",s.ends[v]/1000.0));});
        });dialog.show();
    }
    private EditText field(LinearLayout parent,String label,long ms){
        parent.addView(text(label,13,MUTED));LinearLayout row=horizontal();EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(20);e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);e.setText(String.format(Locale.US,"%.3f",ms/1000.0));e.setContentDescription(label+" 시간 초");
        Button minus=button("−0.05",()->step(e,-.05)),plus=button("+0.05",()->step(e,.05));weighted(row,minus);weighted(row,e);weighted(row,plus);parent.addView(row);return e;
    }
    private void step(EditText e,double delta){try{e.setText(String.format(Locale.US,"%.3f",Math.max(0,Double.parseDouble(e.getText().toString())+delta)));}catch(Exception ignored){}}
}
