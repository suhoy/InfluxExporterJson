package exporter.influx;

import com.sun.org.apache.xerces.internal.impl.dv.xs.BooleanDV;
import okhttp3.OkHttpClient;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONObject;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

/*
Example run
java -jar InfluxExporterJson-1.1.jar -config C:\Users\suh1995\Documents\IDEA\InfluxExporterJson\src\main\resources\config.txt -out .\out -name stat.json -times 2022-01-12T20:55:20 2022-01-12T21:01:20 -durati
ons 00:05:00 -profiles 100 200
 */

public class Main {

    //хранилище аргументов
    final static Map<String, List<String>> args = new HashMap<>();
    //хранилище конфига
    final static Properties prop = new Properties();
    //подключение к бд
    public static InfluxDB influxDB = null;
    //результат запроса
    public static QueryResult qr = null;
    //запись в файл
    private static FileWriter file;


    public static void main(String[] arg) {
        try {
            System.out.println("\n==========InfluxExporterJson started==========");

            //читаем аргументы
            ReadParams(arg);

            //читаем конфиг
            ReadProps(args.get("config").get(0));

            //создаем директорию под json
            CreatefolderFilePath();

            //конечный json
            JSONArray fullja = new JSONArray();

            //по количеству times
            for (int h = 0; h < args.get("times").size(); h++) {
                String time = "";
                String duration = "";
                String profile = "";

                //i-ый отрезок
                time = args.get("times").get(h);

                //если указан 1 duration - берём его для всех периодов
                if (args.get("times").size() == args.get("durations").size()) {
                    duration = args.get("durations").get(h);
                } else {
                    duration = args.get("durations").get(0);
                }

                //если указан 1 profiles - берём его для всех периодов
                if (args.get("times").size() == args.get("profiles").size()) {
                    profile = args.get("profiles").get(h);
                } else {
                    profile = args.get("profiles").get(0);
                }

                System.out.println("\n" + h + ") Getting data for:");
                System.out.println("timeStart=" + time);
                System.out.println("duration=" + duration);
                System.out.println("profile=" + profile);


                //json объект под период
                JSONObject jo_period = new JSONObject();

                //создаем start и finish в utc
                String strCurrentStart = Utils.convertToUTC(time);
                String strCurrentFinish = Utils.convertToUTC(Utils.sumTime(time, duration));

                //для расчёта профиля
                double seconds = Utils.getSecondsBetween(time, Utils.sumTime(time, duration));

                //периоды для tag в json
                String strCurrentStartMoscow = Utils.convertToSimpleMoscowJson(time);
                String strCurrentFinishMoscow = Utils.convertToSimpleMoscowJson(Utils.sumTime(time, duration));

                jo_period.put("time_start", strCurrentStartMoscow);
                jo_period.put("time_finish", strCurrentFinishMoscow);
                jo_period.put("profile", Double.parseDouble(profile));
                jo_period.put("about", "");


                //хттп клиент
                OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient().newBuilder()
                        .connectTimeout(10, TimeUnit.MINUTES)
                        .readTimeout(10, TimeUnit.MINUTES)
                        .writeTimeout(10, TimeUnit.MINUTES);

                //коннект к инфлюксу
                influxDB = InfluxDBFactory.connect(prop.getProperty("influx.url"), prop.getProperty("influx.user"), prop.getProperty("influx.password"), okHttpClientBuilder);


                //считаем количество родительских скриптов
                int scripts_count = 1;
                while (prop.getProperty("s" + scripts_count + ".name") != null) {
                    scripts_count++;
                }
                //dont touch
                scripts_count--;

                //считаем количество sql
                int sql_count = 1;
                while (prop.getProperty("sql" + sql_count + ".query") != null) {
                    sql_count++;
                }
                //dont touch
                sql_count--;

                //заполняем json даными из конфига и аргументов
                JSONArray stats = new JSONArray();
                double tps = 0;
                for (int i = 1; i < scripts_count + 1; i++) {
                    JSONObject jscript = new JSONObject();

                    jscript.put("script", prop.getProperty("s" + i + ".name"));
                    jscript.put("sla", Double.parseDouble(prop.getProperty("s" + i + ".sla")));
                    jscript.put("profile", Double.parseDouble(prop.getProperty("s" + i + ".tps")) * seconds * Double.parseDouble(profile) / 100.0);

                    int child_id = 1;
                    JSONArray childs = new JSONArray();
                    while (prop.getProperty("s" + i + ".c" + child_id + ".name") != null) {
                        JSONObject child = new JSONObject();
                        child.put("script", prop.getProperty("s" + i + ".c" + child_id + ".name"));
                        child.put("sla", Double.parseDouble(prop.getProperty("s" + i + ".c" + child_id + ".sla")));
                        child.put("profile", Double.parseDouble(prop.getProperty("s" + i + ".c" + child_id + ".tps")) * seconds * Double.parseDouble(profile) / 100.0);
                        childs.put(child);
                        child_id++;
                    }
                    jscript.put("child_list", childs);

                    stats.put(jscript);
                    tps += Double.parseDouble(prop.getProperty("s" + i + ".tps"));
                }
                jo_period.put("stats", stats);
                jo_period.put("tps", tps * Double.parseDouble(profile) / 100.0);


                //заполнили json, теперь запросы в influx

                for (int s = 1; s < sql_count + 1; s++) {
                    String sql = prop.getProperty("sql" + s + ".query");
                    String sql_type = prop.getProperty("sql" + s + ".type");
                    sql = sql.replaceAll("__start__", strCurrentStart);
                    sql = sql.replaceAll("__finish__", strCurrentFinish);
                    System.out.println("sql " + sql_type + "\t[" + s + "] = " + sql);
                    String sql_column = prop.getProperty("sql" + s + ".query").split(" GROUP BY ")[1].replaceAll("\"", "");

                    //выполняем запрос
                    qr = influxDB.query(new Query(sql, prop.getProperty("influx.database")));
                    if (qr.getResults().get(0).getSeries() != null) {
                        System.out.println("query results" + "\t[" + s + "] = " + qr.getResults() + "\r\n");
                        HashMap<String, Double> result = new HashMap<>();
                        for (QueryResult.Series sr : qr.getResults().get(0).getSeries()) {
                            int influx_column_id = sr.getColumns().indexOf(sql_type);
                            result.put(sr.getTags().get(sql_column), Double.parseDouble(sr.getValues().get(0).get(influx_column_id).toString()));
                        }

                        if (sql_type.equalsIgnoreCase("pass_count") || sql_type.equalsIgnoreCase("fail_count")) {
                            for (int i = 0; i < jo_period.getJSONArray("stats").length(); i++) {
                                String current_script = jo_period.getJSONArray("stats").getJSONObject(i).getString("script");

                                //для parent
                                if (result.containsKey(current_script)) {
                                    jo_period.getJSONArray("stats").getJSONObject(i).put(sql_type, result.get(current_script));
                                }
                                //для childs
                                // всё тоже самое, что и для parents, но в обращении добавилось .getJSONArray("child_list").getJSONObject(c), а current_script заменилось на current_child
                                if (!jo_period.getJSONArray("stats").getJSONObject(i).isNull("child_list") &&
                                        jo_period.getJSONArray("stats").getJSONObject(i).getJSONArray("child_list").length() > 0) {
                                    for (int c = 0; c < jo_period.getJSONArray("stats").getJSONObject(i).getJSONArray("child_list").length(); c++) {
                                        String current_child = jo_period.getJSONArray("stats").getJSONObject(i).getJSONArray("child_list").getJSONObject(c).getString("script");
                                        if (result.containsKey(current_child)) {
                                            jo_period.getJSONArray("stats").getJSONObject(i).getJSONArray("child_list").getJSONObject(c).put(sql_type, result.get(current_child));
                                        }
                                    }
                                }
                            }
                        }//конец для pass_count и fail_count

                        //для времён добавляем json со статистикой
                        if (sql_type.equalsIgnoreCase("pass_time") || sql_type.equalsIgnoreCase("fail_time")) {
                            String current_tag = prop.getProperty("sql" + s + ".tag");
                            for (int i = 0; i < jo_period.getJSONArray("stats").length(); i++) {
                                String current_script = jo_period.getJSONArray("stats").getJSONObject(i).getString("script");

                                //для parent
                                if (result.containsKey(current_script)) {
                                    //если stat_time уже есть
                                    if (!jo_period.getJSONArray("stats").getJSONObject(i).isNull("stat_time")) {
                                        boolean st_added = false;
                                        //по всем stat_time
                                        for (int st = 0; st < jo_period.getJSONArray("stats").getJSONObject(i).getJSONArray("stat_time").length(); st++) {
                                            //если найден тэг
                                            if (jo_period.getJSONArray("stats").getJSONObject(i).getJSONArray("stat_time").getJSONObject(st).getString("tag").equalsIgnoreCase(current_tag)) {
                                                jo_period.getJSONArray("stats").getJSONObject(i).getJSONArray("stat_time").getJSONObject(st).put(sql_type, result.get(current_script));
                                                st_added = true;
                                                break;
                                            }
                                        }
                                        //если stat_time существовали, но тега такого не было
                                        if (!st_added) {
                                            JSONObject stat_time = new JSONObject();
                                            stat_time.put("tag", current_tag);
                                            stat_time.put(sql_type, result.get(current_script));
                                            jo_period.getJSONArray("stats").getJSONObject(i).getJSONArray("stat_time").put(stat_time);
                                        }
                                    }
                                    // если stat_time не существует
                                    else {
                                        JSONObject stat_time = new JSONObject();
                                        stat_time.put("tag", current_tag);
                                        stat_time.put(sql_type, result.get(current_script));

                                        JSONArray ja_stat_time = new JSONArray();
                                        ja_stat_time.put(stat_time);

                                        jo_period.getJSONArray("stats").getJSONObject(i).put("stat_time", ja_stat_time);

                                    }
                                }//конец для parent

                                //по childs
                                //всё тоже самое, что и для parents, но в обращении добавилось .getJSONArray("child_list").getJSONObject(c), а current_script заменилось на current_child
                                if (!jo_period.getJSONArray("stats").getJSONObject(i).isNull("child_list") &&
                                        jo_period.getJSONArray("stats").getJSONObject(i).getJSONArray("child_list").length() > 0) {
                                    for (int c = 0; c < jo_period.getJSONArray("stats").getJSONObject(i).getJSONArray("child_list").length(); c++) {
                                        String current_child = jo_period.getJSONArray("stats").getJSONObject(i).getJSONArray("child_list").getJSONObject(c).getString("script");
                                        if (result.containsKey(current_child)) {
                                            //если stat_time уже есть
                                            if (!jo_period.getJSONArray("stats").getJSONObject(i).getJSONArray("child_list").getJSONObject(c).isNull("stat_time")) {
                                                boolean st_added = false;
                                                //по всем stat_time
                                                for (int st = 0; st < jo_period.getJSONArray("stats").getJSONObject(i).getJSONArray("child_list").getJSONObject(c).getJSONArray("stat_time").length(); st++) {
                                                    //если найден тэг
                                                    if (jo_period.getJSONArray("stats").getJSONObject(i).getJSONArray("child_list").getJSONObject(c).getJSONArray("stat_time").getJSONObject(st).getString("tag").equalsIgnoreCase(current_tag)) {
                                                        jo_period.getJSONArray("stats").getJSONObject(i).getJSONArray("child_list").getJSONObject(c).getJSONArray("stat_time").getJSONObject(st).put(sql_type, result.get(current_child));
                                                        st_added = true;
                                                        break;
                                                    }
                                                }
                                                //если stat_time существовали, но тега такого не было
                                                if (!st_added) {
                                                    JSONObject stat_time = new JSONObject();
                                                    stat_time.put("tag", current_tag);
                                                    stat_time.put(sql_type, result.get(current_child));
                                                    jo_period.getJSONArray("stats").getJSONObject(i).getJSONArray("child_list").getJSONObject(c).getJSONArray("stat_time").put(stat_time);
                                                }
                                            }
                                            // если stat_time не существует
                                            else {
                                                JSONObject stat_time = new JSONObject();
                                                stat_time.put("tag", current_tag);
                                                stat_time.put(sql_type, result.get(current_child));

                                                JSONArray ja_stat_time = new JSONArray();
                                                ja_stat_time.put(stat_time);

                                                jo_period.getJSONArray("stats").getJSONObject(i).getJSONArray("child_list").getJSONObject(c).put("stat_time", ja_stat_time);

                                            }
                                        }
                                    }//конец for childs
                                }//конец if childs


                            }

                        }
                    }


                }

                fullja.put(jo_period);
            }//конец по периоду

            //пишем и сохраняем
            System.out.println("\nResult json=" + fullja.toString());

            Writer fstream = null;
            fstream = new OutputStreamWriter(new FileOutputStream(prop.getProperty("out")), StandardCharsets.UTF_8);
            fstream.write(fullja.toString());
            fstream.close();

            System.out.println("\nFile saved=" + prop.getProperty("out"));

            System.out.println("\n==========InfluxExporterJson finished==========");
        } catch (Exception ex) {
            ex.printStackTrace();
            System.out.println("\n==========InfluxExporterJson finished==========");
        }
    }

    //чтение аргументов
    public static void ReadParams(String[] arg) {
        List<String> options = null;
        for (int i = 0; i < arg.length; i++) {
            final String a = arg[i];

            if (a.charAt(0) == '-') {
                if (a.length() < 2) {
                    System.err.println("Error at argument " + a);
                    return;
                }

                options = new ArrayList<>();
                args.put(a.substring(1), options);
            } else if (options != null) {
                options.add(a);
            } else {
                System.err.println("Illegal parameter usage");
                return;
            }
        }
        System.out.println("\nStarted with args:");
        for (Map.Entry<String, List<String>> entry : args.entrySet()) {
            System.out.println(entry.getKey() + ":" + entry.getValue());
        }
    }

    public static void ReadProps(String config) {
        try {
            prop.load(new InputStreamReader(new FileInputStream(config), StandardCharsets.UTF_8));
            System.out.println("\nGet config, unsorted:");
            Enumeration keys = prop.keys();
            while (keys.hasMoreElements()) {
                String key = (String) keys.nextElement();
                String value = (String) prop.get(key);
                System.out.println(key + ": " + value);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    //создание фолдера для результатов
    private static void CreatefolderFilePath() {
        String outdir = args.get("out").get(0);
        String outname = args.get("name").get(0);
        try {
            Files.createDirectories(Paths.get(outdir));
            String folderFilePath = outdir + "\\" + outname;
            prop.put("out", folderFilePath);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
