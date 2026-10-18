package com.matael.app;

import android.app.*;import android.os.*;import android.content.*;import android.graphics.Color;import android.graphics.Typeface;import android.graphics.drawable.GradientDrawable;import android.text.*;import android.view.*;import android.widget.*;import android.util.Base64;import android.security.keystore.KeyGenParameterSpec;import android.security.keystore.KeyProperties;
import org.json.*;import java.io.ByteArrayOutputStream;import java.nio.charset.StandardCharsets;import java.security.*;import java.util.*;import javax.crypto.*;import javax.crypto.spec.*;

/** MATAEL 2.1: secure local Notes and Moments with corrected timeout, system theme and startup screen. */
public class MainActivity extends Activity {
 private static final String PREF="matael_security", DATA="matael_vault_v2", ALIAS="matael_notes_aes_key";
 private SharedPreferences prefs; private JSONArray entries=new JSONArray(); private String page="notes"; private String query=""; private int accent=0xFF7895FF; private int bg=0xFF12141D, card=0xFF222738, muted=0xFFABB4CC; private LinearLayout root, list; private boolean unlocked=false; private long backgroundAt=0;
 private static final int PICK_BACKUP=44, SAVE_BACKUP=45;
 private final Handler mainHandler = new Handler(Looper.getMainLooper());
 private long lastInteractionAt = 0;
 private boolean pinDialogVisible = false;
 private boolean backupReminderShown = false;
 private final Runnable idleLockRunnable = new Runnable() {
  @Override public void run() {
   if (!unlocked || prefs == null) return;
   long timeout = prefs.getLong("timeout", 0L);
   if (timeout <= 0) return;
   long remaining = timeout - (System.currentTimeMillis() - lastInteractionAt);
   if (remaining <= 0) {
    unlocked = false;
    mainHandler.removeCallbacks(this);
    askPin();
   } else {
    mainHandler.postDelayed(this, remaining);
   }
  }
 };
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  prefs=getSharedPreferences(PREF,MODE_PRIVATE);
  accent=prefs.getInt("accent",0xFF7895FF);
  page=prefs.getString("start_screen","notes");
  if(!"moments".equals(page)) page="notes";
  applyColors();
  getWindow().setStatusBarColor(bg);
  getWindow().setNavigationBarColor(bg);
  if(prefs.contains("pin_hash")) askPin(); else setupPin();
 }
 private void applyColors(){
  String theme=prefs==null?"dark":prefs.getString("theme","dark");
  boolean light;
  if ("system".equals(theme)) {
   int mode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
   light = mode != android.content.res.Configuration.UI_MODE_NIGHT_YES;
  } else {
   light = "light".equals(theme);
  }
  if(light){bg=0xFFF3F5FB;card=0xFFFFFFFF;muted=0xFF626B80;}else{bg=0xFF10131B;card=0xFF1B2130;muted=0xFFAAB4CA;}
 }
 @Override protected void onPause(){
  super.onPause();
  backgroundAt=System.currentTimeMillis();
  mainHandler.removeCallbacks(idleLockRunnable);
  if(prefs!=null && prefs.getBoolean("lock_background",true)) unlocked=false;
 }
 @Override protected void onResume(){
  super.onResume();
  if(prefs==null) return;
  if(prefs.contains("pin_hash") && unlocked && backgroundAt>0 && !prefs.getBoolean("lock_background",true)) {
   long timeout=prefs.getLong("timeout",0L);
   if(timeout>0 && System.currentTimeMillis()-backgroundAt>=timeout) unlocked=false;
  }
  if(prefs.contains("pin_hash") && !unlocked) {
   mainHandler.postDelayed(()->{if(!unlocked && !pinDialogVisible) askPin();},180);
  } else if(unlocked) {
   lastInteractionAt=System.currentTimeMillis();
   scheduleIdleLock();
  }
  backgroundAt=0;
 }
 @Override public void onUserInteraction(){
  super.onUserInteraction();
  if(unlocked) {
   lastInteractionAt=System.currentTimeMillis();
   scheduleIdleLock();
  }
 }
 private void scheduleIdleLock(){
  mainHandler.removeCallbacks(idleLockRunnable);
  if(!unlocked || prefs==null) return;
  long timeout=prefs.getLong("timeout",0L);
  if(timeout<=0) return;
  if(lastInteractionAt<=0) lastInteractionAt=System.currentTimeMillis();
  mainHandler.postDelayed(idleLockRunnable,Math.max(1,timeout-(System.currentTimeMillis()-lastInteractionAt)));
 }
 private void onPinAccepted(){
  unlocked=true;
  lastInteractionAt=System.currentTimeMillis();
  load();
  showHome();
  scheduleIdleLock();
  maybeBackupReminder();
 }
 private void setupPin(){if(pinDialogVisible)return;pinDialogVisible=true;EditText e=field("4–8 цифр",true);new AlertDialog.Builder(this).setTitle("Создай PIN-код").setMessage("PIN защищает вход в MATAEL").setView(e).setCancelable(false).setPositiveButton("Продолжить",(d,w)->{pinDialogVisible=false;String p=e.getText().toString();if(!p.matches("[0-9]{4,8}")){toast("PIN должен содержать 4–8 цифр");setupPin();return;}confirmPin(p);}).show();}
 private void confirmPin(String p){pinDialogVisible=true;EditText e=field("Повтори PIN",true);new AlertDialog.Builder(this).setTitle("Подтверди PIN").setView(e).setCancelable(false).setPositiveButton("Сохранить",(d,w)->{pinDialogVisible=false;if(!p.equals(e.getText().toString())){toast("PIN не совпали");setupPin();return;}try{byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);String s=Base64.encodeToString(salt,Base64.NO_WRAP);prefs.edit().putString("pin_salt",s).putString("pin_hash",hashPin(p,s)).commit();onPinAccepted();}catch(Exception ex){toast("Не удалось настроить защиту");setupPin();}}).show();}
 private void askPin(){if(pinDialogVisible || isFinishing())return;pinDialogVisible=true;EditText e=field("PIN-код",true);new AlertDialog.Builder(this).setTitle("MATAEL заблокирован").setView(e).setCancelable(false).setPositiveButton("Открыть",(d,w)->{pinDialogVisible=false;try{if(MessageDigest.isEqual(hashPin(e.getText().toString(),prefs.getString("pin_salt","")).getBytes(StandardCharsets.UTF_8),prefs.getString("pin_hash","").getBytes(StandardCharsets.UTF_8))){onPinAccepted();}else{toast("Неверный PIN");mainHandler.postDelayed(()->askPin(),180);}}catch(Exception x){toast("Ошибка проверки PIN");mainHandler.postDelayed(()->askPin(),180);}}).show();}
 private String hashPin(String pin,String salt)throws Exception{PBEKeySpec spec=new PBEKeySpec(pin.toCharArray(),Base64.decode(salt,Base64.NO_WRAP),180000,256);byte[] out=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();spec.clearPassword();return Base64.encodeToString(out,Base64.NO_WRAP);}
 private SecretKey key()throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(ks.containsAlias(ALIAS))return (SecretKey)ks.getKey(ALIAS,null);KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());return g.generateKey();}
 private String encrypt(String s)throws Exception{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());return "v1:"+Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(s.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);}
 private String decrypt(String s)throws Exception{String[] p=s.split(":",3);if(p.length!=3||!p[0].equals("v1"))throw new Exception("format");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(p[1],Base64.NO_WRAP)));return new String(c.doFinal(Base64.decode(p[2],Base64.NO_WRAP)),StandardCharsets.UTF_8);}
 private void load(){try{String raw=prefs.getString(DATA,null);entries=raw==null?new JSONArray():new JSONArray(decrypt(raw));}catch(Exception e){entries=new JSONArray();toast("Не удалось расшифровать хранилище. Исходные данные не удалены.");}}
 private boolean save(){try{return prefs.edit().putString(DATA,encrypt(entries.toString())).commit();}catch(Exception e){toast("Не удалось зашифровать данные");return false;}}
 private void showHome(){
  applyColors(); getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
  getWindow().getDecorView().setSystemUiVisibility(bg==0xFFF3F5FB?(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR):0);
  root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(18),dp(10),dp(18),dp(12));root.setBackgroundColor(bg);root.setFitsSystemWindows(true);
  LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(0,dp(4),0,dp(2));
  LinearLayout brandBox=new LinearLayout(this);brandBox.setOrientation(1);
  TextView brand=text("MATAEL",29,accent,true);brand.setLetterSpacing(0.025f);brand.setPadding(0,0,0,0);brandBox.addView(brand);
  TextView subtitle=text("ТВОЁ ЛИЧНОЕ ПРОСТРАНСТВО",11,muted,false);subtitle.setPadding(1,0,0,0);brandBox.addView(subtitle);
  top.addView(brandBox,new LinearLayout.LayoutParams(0,-2,1));Button settings=button("⚙");settings.setTextSize(20);settings.setContentDescription("Настройки");top.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(48)));settings.setOnClickListener(v->settings());root.addView(top,spaced(0,dp(16)));
  LinearLayout tabs=new LinearLayout(this);tabs.setGravity(Gravity.CENTER_VERTICAL);tabs.setPadding(dp(4),dp(4),dp(4),dp(4));tabs.setBackground(shape(card,18));
  Button notes=button("Заметки"),moments=button("Moments"),add=button("＋");
  styleTab(notes,page.equals("notes"));styleTab(moments,page.equals("moments"));styleTab(add,false);add.setTextSize(23);
  notes.setOnClickListener(v->{page="notes";showHome();});moments.setOnClickListener(v->{page="moments";showHome();});add.setOnClickListener(v->quickCreate());
  tabs.addView(notes,new LinearLayout.LayoutParams(0,dp(44),1));LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,dp(44),1);mp.leftMargin=dp(4);tabs.addView(moments,mp);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(48),dp(44));ap.leftMargin=dp(4);tabs.addView(add,ap);root.addView(tabs,spaced(0,dp(14)));
  EditText search=field(page.equals("moments")?"Поиск воспоминаний":"Поиск заметок",false);search.setSingleLine(true);search.setPadding(dp(14),0,dp(14),0);search.setBackground(shape(card,14));search.setText(query);root.addView(search,new LinearLayout.LayoutParams(-1,dp(50)));
  LinearLayout row=new LinearLayout(this);row.setPadding(0,dp(10),0,dp(12));Button fav=button("★  Избранное"),backup=button("Копия");fav.setTextSize(13);backup.setTextSize(13);fav.setOnClickListener(v->{page="favorites";showHome();});backup.setOnClickListener(v->backupMenu());row.addView(fav,new LinearLayout.LayoutParams(0,dp(44),1));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(44),1);bp.leftMargin=dp(8);row.addView(backup,bp);root.addView(row);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);list=new LinearLayout(this);list.setOrientation(1);list.setPadding(0,dp(2),0,dp(10));scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  Button lock=button("Заблокировать MATAEL");lock.setTextSize(13);lock.setTextColor(0xFFFFFFFF);lock.setBackground(shape(0xFF293149,16));lock.setOnClickListener(v->{unlocked=false;mainHandler.removeCallbacks(idleLockRunnable);askPin();});root.addView(lock,new LinearLayout.LayoutParams(-1,dp(48)));
  setContentView(root);render(query);
  search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){query=s.toString();render(query);}public void afterTextChanged(Editable e){}});
 }
 private void styleTab(Button b,boolean selected){b.setTextSize(13);b.setTextColor(selected?Color.WHITE:muted);b.setBackground(shape(selected?accent:card,13));}
 private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
 private void render(String q){if(list==null)return;list.removeAllViews();String needle=q.toLowerCase(Locale.ROOT);ArrayList<JSONObject> items=new ArrayList<>();for(int i=0;i<entries.length();i++){JSONObject o=entries.optJSONObject(i);if(o==null)continue;String type=o.optString("type","note");if(page.equals("notes")&&!type.equals("note"))continue;if(page.equals("moments")&&!type.equals("moment"))continue;if(page.equals("favorites")&&!o.optBoolean("favorite"))continue;if(o.optBoolean("hidden")&&!o.optBoolean("showLockCard"))continue;String hay=(o.optString("title")+" "+o.optString("body")+" "+o.optString("category")+" "+o.optString("mood")).toLowerCase(Locale.ROOT);if(!needle.isEmpty()&&!hay.contains(needle))continue;items.add(o);}items.sort((a,b)->{int pin=Boolean.compare(b.optBoolean("pinned"),a.optBoolean("pinned"));return pin!=0?pin:Long.compare(b.optLong("updated"),a.optLong("updated"));});for(JSONObject o:items){LinearLayout c=new LinearLayout(this);c.setOrientation(1);c.setPadding(14,12,14,10);c.setBackground(shape(card,18));LinearLayout.LayoutParams cp=spaced(5,6);TextView title=text((o.optBoolean("pinned")?"📌 ":"")+(o.optBoolean("favorite")?"★ ":"")+(o.optBoolean("hidden")?"🔒 ":"")+o.optString("title","Без названия"),17,accent,true);c.addView(title);String body=o.optBoolean("hidden")?"Содержимое скрыто":o.optString("body","");TextView preview=text(body,14,bg==0xFFF3F5FB?0xFF30384A:0xFFE0E5F0,false);preview.setMaxLines(3);c.addView(preview);String meta=o.optString("category","");if(!o.optString("mood","").isEmpty())meta+="  "+o.optString("mood");if(!meta.isEmpty())c.addView(text(meta,12,muted,false));LinearLayout actions=new LinearLayout(this);Button star=button(o.optBoolean("favorite")?"★":"☆"),pin=button(o.optBoolean("pinned")?"Открепить":"Закрепить"),edit=button("Открыть");star.setOnClickListener(v->{try{o.put("favorite",!o.optBoolean("favorite"));o.put("updated",System.currentTimeMillis());save();render(query);}catch(Exception ex){toast("Не удалось обновить избранное");}});pin.setOnClickListener(v->{try{o.put("pinned",!o.optBoolean("pinned"));save();render(query);}catch(Exception ex){toast("Не удалось закрепить запись");}});edit.setOnClickListener(v->editEntry(o));actions.addView(star,new LinearLayout.LayoutParams(0,42,1));actions.addView(pin,new LinearLayout.LayoutParams(0,42,1));actions.addView(edit,new LinearLayout.LayoutParams(0,42,1));c.addView(actions);c.setOnClickListener(v->editEntry(o));list.addView(c,cp);}if(items.isEmpty()){TextView empty=text(page.equals("moments")?"Пока нет воспоминаний. Нажми ＋, чтобы добавить первое.":"Пока нет записей. Нажми ＋, чтобы создать.",14,muted,false);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(20),dp(42),dp(20),dp(36));empty.setTextSize(15);list.addView(empty);}}
 private void quickCreate(){premiumMenu("Создать запись","Выбери, что хочешь сохранить",new String[]{"Заметка","Короткое воспоминание","История воспоминания"},new String[]{"Мысли, планы и важные дела","Момент, который хочется запомнить","История с подробностями и эмоциями"},new Runnable[]{()->createEntry(0),()->createEntry(1),()->createEntry(2)});}
 private void createEntry(int w){page=w==0?"notes":"moments";JSONObject o=new JSONObject();try{o.put("id",UUID.randomUUID().toString());o.put("type",w==0?"note":"moment");o.put("title","");o.put("body","");o.put("created",System.currentTimeMillis());o.put("updated",System.currentTimeMillis());o.put("favorite",false);o.put("pinned",false);o.put("hidden",false);o.put("showLockCard",true);o.put("category",w==0?"Заметки":"Наша история");o.put("mood","🙂");o.put("photos",new JSONArray());entries.put(o);editEntry(o);}catch(Exception e){toast("Ошибка создания");}}
 private void editEntry(JSONObject o){LinearLayout form=new LinearLayout(this);form.setPadding(8,4,8,4);form.setOrientation(1);EditText title=field("Название",false),body=field("Текст записи",false);title.setText(o.optString("title",""));body.setText(o.optString("body",""));body.setMinLines(5);form.addView(title);form.addView(body);Spinner cat=new Spinner(this);String[] cats=o.optString("type").equals("moment")?new String[]{"Наша история","Семья","Моя жизнь","Самое дорогое","Своя категория"}:new String[]{"Без папки","Работа","Личное","Идеи","Своя папка"};cat.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,cats));form.addView(cat);EditText customCat=field("Название своей категории (необязательно)",false);form.addView(customCat);Spinner mood=new Spinner(this);mood.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"🙂","❤️","🥰","😂","😢","😌","🔥","🙏"}));form.addView(mood);CheckBox favorite=new CheckBox(this);favorite.setText("Добавить в избранное");favorite.setChecked(o.optBoolean("favorite"));form.addView(favorite);CheckBox pinned=new CheckBox(this);pinned.setText("Закрепить наверху");pinned.setChecked(o.optBoolean("pinned"));form.addView(pinned);CheckBox hidden=new CheckBox(this);hidden.setText("Закрытая запись (скрыть содержимое)");hidden.setChecked(o.optBoolean("hidden"));form.addView(hidden);CheckBox lockCard=new CheckBox(this);lockCard.setText("Показывать карточку с замком");lockCard.setChecked(o.optBoolean("showLockCard",true));form.addView(lockCard);
  new AlertDialog.Builder(this).setTitle(o.optString("type").equals("moment")?"Редактор Moments":"Редактор заметки").setView(form).setNegativeButton("Отмена",null).setNeutralButton("Удалить",(d,w)->{for(int i=0;i<entries.length();i++)if(entries.optJSONObject(i)==o){entries.remove(i);break;}save();showHome();}).setPositiveButton("Сохранить",(d,w)->{try{o.put("title",title.getText().toString().trim());o.put("body",body.getText().toString());String chosen=(String)cat.getSelectedItem();o.put("category",customCat.getText().toString().trim().isEmpty()?chosen:customCat.getText().toString().trim());o.put("mood",(String)mood.getSelectedItem());o.put("favorite",favorite.isChecked());o.put("pinned",pinned.isChecked());o.put("hidden",hidden.isChecked());o.put("showLockCard",lockCard.isChecked());o.put("updated",System.currentTimeMillis());save();showHome();}catch(Exception e){toast("Не удалось сохранить запись");}}).show();}
 private void settings(){premiumMenu("Настройки MATAEL","Персонализируй своё пространство",new String[]{"Внешний вид","Цветовой акцент","Блокировка в фоне","Автоблокировка","Стартовый экран","Резервные копии"},new String[]{"Тема приложения","Выбери свой цвет","Требовать PIN после сворачивания","Время до автоматической блокировки","Что открывать при запуске","Статус и создание копии"},new Runnable[]{()->chooseTheme(),()->chooseAccent(),()->{boolean n=!prefs.getBoolean("lock_background",true);prefs.edit().putBoolean("lock_background",n).apply();toast("Блокировка в фоне: "+(n?"включена":"выключена"));},()->chooseTimeout(),()->premiumMenu("Стартовый экран","Выбери экран после входа",new String[]{"Заметки","Moments"},new String[]{"Твои записи и идеи","Личный архив воспоминаний"},new Runnable[]{()->{page="notes";prefs.edit().putString("start_screen",page).apply();showHome();},()->{page="moments";prefs.edit().putString("start_screen",page).apply();showHome();}}),()->{long last=prefs.getLong("last_backup",0);premiumMenu("Резервная копия",last==0?"Копия ещё не создавалась":"Последняя копия: "+new Date(last),new String[]{"Создать копию","Восстановить копию"},new String[]{"Зашифровать и сохранить записи","Восстановить из файла"},new Runnable[]{()->exportBackup(),()->importBackup()});}});}
 private void chooseTheme(){String[] names={"Тёмная","Светлая","Системная"};String[] desc={"Глубокий фон и мягкие акценты","Светлый и чистый интерфейс","Следовать настройкам телефона"};String[] keys={"dark","light","system"};Runnable[] acts=new Runnable[3];for(int i=0;i<3;i++){final int j=i;acts[i]=()->{prefs.edit().putString("theme",keys[j]).apply();showHome();};}premiumMenu("Тема оформления","Выбери комфортный вариант",names,desc,acts);}
 private void chooseAccent(){String[] names={"Ледяной синий","Лавандовый","Изумрудный","Золотой","Розовый"};String[] desc={"Спокойный холодный акцент","Мягкий фиолетовый оттенок","Свежий зелёный оттенок","Тёплый золотой оттенок","Нежный розовый оттенок"};int[] colors={0xFF7895FF,0xFFB39DDB,0xFF4DB6AC,0xFFE0B15A,0xFFF48FB1};Runnable[] acts=new Runnable[5];for(int i=0;i<5;i++){final int j=i;acts[i]=()->{accent=colors[j];prefs.edit().putInt("accent",accent).apply();showHome();};}premiumMenu("Цветовой акцент","Выбери характер MATAEL",names,desc,acts);}
 private void chooseTimeout(){String[] names={"Выключена","30 секунд","1 минута","5 минут","10 минут"};String[] desc={"Не блокировать автоматически","Для коротких пауз","Рекомендуемый баланс","Для длительных задач","Для редких перерывов"};long[] vals={0,30000,60000,300000,600000};Runnable[] acts=new Runnable[5];for(int i=0;i<5;i++){final int j=i;acts[i]=()->{prefs.edit().putLong("timeout",vals[j]).apply();lastInteractionAt=System.currentTimeMillis();scheduleIdleLock();toast("Настройка сохранена");};}premiumMenu("Автоблокировка","Защити записи, когда отходишь от телефона",names,desc,acts);}
 private void premiumMenu(String title,String subtitle,String[] labels,String[] descriptions,Runnable[] actions){final Dialog dialog=new Dialog(this);LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(20),dp(20),dp(20),dp(14));panel.setBackground(shape(bg==0xFFF3F5FB?0xFFFFFFFF:0xFF1B2130,24));TextView heading=text(title,21,bg==0xFFF3F5FB?0xFF202638:Color.WHITE,true);heading.setPadding(dp(2),dp(2),dp(2),dp(5));panel.addView(heading);TextView sub=text(subtitle,13,muted,false);sub.setPadding(dp(2),0,dp(2),dp(14));panel.addView(sub);ScrollView scroll=new ScrollView(this);LinearLayout rows=new LinearLayout(this);rows.setOrientation(1);for(int i=0;i<labels.length;i++){final int j=i;LinearLayout item=new LinearLayout(this);item.setOrientation(1);item.setPadding(dp(14),dp(12),dp(12),dp(12));item.setBackground(shape(card,16));TextView name=text(labels[i],16,bg==0xFFF3F5FB?0xFF202638:Color.WHITE,true);name.setPadding(0,0,0,dp(3));TextView detail=text(descriptions[i],12,muted,false);detail.setPadding(0,0,0,0);item.addView(name);item.addView(detail);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.bottomMargin=dp(8);rows.addView(item,ip);item.setOnClickListener(v->{dialog.dismiss();actions[j].run();});}scroll.addView(rows);panel.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(dp(labels.length>4?390:320),getResources().getDisplayMetrics().heightPixels/2)));TextView close=text("Закрыть",14,accent,true);close.setGravity(Gravity.CENTER);close.setPadding(0,dp(12),0,dp(4));close.setOnClickListener(v->dialog.dismiss());panel.addView(close);dialog.setContentView(panel);Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams lp=w.getAttributes();lp.width=getResources().getDisplayMetrics().widthPixels-dp(36);lp.height=-2;lp.dimAmount=.62f;w.setAttributes(lp);}dialog.show();if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels-dp(36),-2);}}
 private void backupMenu(){long last=prefs.getLong("last_backup",0);String status=last==0?"Резервная копия ещё не создавалась":"Последняя копия: "+new java.text.SimpleDateFormat("dd.MM.yyyy · HH:mm",java.util.Locale.getDefault()).format(new Date(last));premiumMenu("Резервная копия",status,new String[]{"Создать зашифрованную копию","Восстановить из копии"},new String[]{"Экспортировать записи в защищённый файл","Импортировать файл и восстановить записи"},new Runnable[]{()->exportBackup(),()->importBackup()});}
 private void maybeBackupReminder(){if(backupReminderShown)return;long last=prefs.getLong("last_backup",0);long age=System.currentTimeMillis()-last;long thirtyDays=30L*24*60*60*1000;if(last!=0 && age<thirtyDays)return;backupReminderShown=true;new AlertDialog.Builder(this).setTitle("Резервная копия MATAEL").setMessage(last==0?"Вы ещё не создавали резервную копию. Создайте зашифрованный файл, чтобы иметь возможность восстановить записи.":"Последняя резервная копия старше 30 дней. Рекомендуется создать новую копию.").setNegativeButton("Позже",null).setPositiveButton("Создать копию",(d,w)->exportBackup()).show();}
 private void exportBackup(){try{EditText e=field("Пароль для шифрования копии",true);new AlertDialog.Builder(this).setTitle("Зашифрованная резервная копия").setView(e).setNegativeButton("Отмена",null).setPositiveButton("Создать",(d,w)->{try{String p=e.getText().toString();if(p.length()<8){toast("Пароль должен быть не короче 8 символов");return;}byte[] salt=new byte[16],iv=new byte[12];SecureRandom r=new SecureRandom();r.nextBytes(salt);r.nextBytes(iv);SecretKeyFactory f=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");SecretKey k=f.generateSecret(new PBEKeySpec(p.toCharArray(),salt,180000,256));Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(k.getEncoded(),"AES"),new GCMParameterSpec(128,iv));String out="MATAEL_BACKUP_V2\n"+Base64.encodeToString(salt,2)+"\n"+Base64.encodeToString(iv,2)+"\n"+Base64.encodeToString(c.doFinal(entries.toString().getBytes(StandardCharsets.UTF_8)),2);Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"MATAEL_backup.json");pendingBackup=out;startActivityForResult(i,SAVE_BACKUP);}catch(Exception x){toast("Не удалось создать копию");}}).show();}catch(Exception e){toast("Ошибка экспорта");}}
 private String pendingBackup="";
 private void importBackup(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");startActivityForResult(i,PICK_BACKUP);}
 @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(res!=RESULT_OK||data==null)return;try{if(req==SAVE_BACKUP){java.io.OutputStream os=getContentResolver().openOutputStream(data.getData());os.write(pendingBackup.getBytes(StandardCharsets.UTF_8));os.close();prefs.edit().putLong("last_backup",System.currentTimeMillis()).apply();toast("Резервная копия сохранена");}else if(req==PICK_BACKUP){java.io.InputStream in=getContentResolver().openInputStream(data.getData());ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))>0){if(out.size()>20*1024*1024)throw new Exception("too large");out.write(b,0,n);}in.close();String raw=new String(out.toByteArray(),StandardCharsets.UTF_8);importEncrypted(raw);}}catch(Exception e){toast("Не удалось обработать резервную копию");}}
 private void importEncrypted(String raw)throws Exception{String[] p=raw.split("\\n",4);if(p.length!=4||!p[0].equals("MATAEL_BACKUP_V2"))throw new Exception("format");EditText e=field("Пароль резервной копии",true);new AlertDialog.Builder(this).setTitle("Импорт копии").setView(e).setNegativeButton("Отмена",null).setPositiveButton("Восстановить",(d,w)->{try{byte[] salt=Base64.decode(p[1],2),iv=Base64.decode(p[2],2);SecretKey k=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(e.getText().toString().toCharArray(),salt,180000,256));Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(k.getEncoded(),"AES"),new GCMParameterSpec(128,iv));JSONArray restored=new JSONArray(new String(c.doFinal(Base64.decode(p[3],2)),StandardCharsets.UTF_8));new AlertDialog.Builder(this).setTitle("Заменить текущие записи?").setMessage("Текущие записи будут заменены содержимым копии.").setNegativeButton("Отмена",null).setPositiveButton("Заменить",(dd,ww)->{entries=restored;save();showHome();toast("Копия восстановлена");}).show();}catch(Exception x){toast("Неверный пароль или повреждённая копия");}}).show();}
 private EditText field(String hint,boolean pin){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(pin);e.setTextColor(bg==0xFFF3F5FB?0xFF202638:Color.WHITE);e.setHintTextColor(muted);e.setTextSize(16);e.setInputType(pin?android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD:android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);return e;}
 private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setPadding(2,5,2,5);return t;}
 private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(8),0,dp(8),0);b.setGravity(Gravity.CENTER);b.setBackground(shape(accent,14));if(android.os.Build.VERSION.SDK_INT>=21)b.setStateListAnimator(null);return b;}
 private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius*getResources().getDisplayMetrics().density);d.setStroke(1,Color.argb(30,255,255,255));return d;}
 private LinearLayout.LayoutParams spaced(int top,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=top;p.bottomMargin=bottom;return p;}
 private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
