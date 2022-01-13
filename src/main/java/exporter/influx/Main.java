package exporter.influx;

import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONObject;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;

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
            //читаем аргументы
            ReadParams(arg);

            //читаем конфиг
            ReadProps();

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

                //по количеству тегов
                for (int t = 1; t < Integer.parseInt(prop.getProperty("tags.count")) + 1; t++) {

                    System.out.println("\n" + h + "." + t + ") Tag: "+ prop.getProperty("tag" + t + ".name"));
                    //System.out.println("tag.name=" + prop.getProperty("tag" + t + ".name"));
                    //System.out.println("tag.count_function=" + prop.getProperty("tag" + t + ".count_function"));
                    //System.out.println("tag.time_function=" + prop.getProperty("tag" + t + ".time_function"));

                    //хттп клиент
                    OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient().newBuilder()
                            .connectTimeout(40, TimeUnit.SECONDS)
                            .readTimeout(60, TimeUnit.SECONDS)
                            .writeTimeout(60, TimeUnit.SECONDS);


                    //коннект к инфлюксу
                    influxDB = InfluxDBFactory.connect(prop.getProperty("influx.url"), prop.getProperty("influx.user"), prop.getProperty("influx.password"), okHttpClientBuilder);

                    //создаем start и finish в utc
                    String strCurrentStart = Utils.convertToUTC(time);
                    String strCurrentFinish = Utils.convertToUTC(Utils.sumTime(time, duration));

                    //периоды для tag в json
                    String strCurrentStartMoscow = Utils.convertToSimpleMoscow(time);
                    String strCurrentFinishMoscow = Utils.convertToSimpleMoscow(Utils.sumTime(time, duration));
                    String tag = "Период " + strCurrentStartMoscow + " - " + strCurrentFinishMoscow + " , " + prop.getProperty("tag" + t + ".name");

                    //для профиля
                    double seconds = Utils.getSecondsBetween(time, Utils.sumTime(time, duration));

                    //по количеству запросов
                    JSONArray ja = new JSONArray();
                    for (int s = 1; s < Integer.parseInt(prop.getProperty("sql.count")) + 1; s++) {
                        //заполняем запрос
                        String sql = prop.getProperty("sql" + s + ".query");
                        sql = sql.replaceAll("__start__", strCurrentStart);
                        sql = sql.replaceAll("__finish__", strCurrentFinish);
                        sql = sql.replaceAll("__function__", prop.getProperty("tag" + t + ".function." + prop.getProperty("sql" + s + ".type")));
                        sql = sql.replaceAll("__script__", prop.getProperty("sql" + s + ".script"));
                        sql = sql.replaceAll("__column__", prop.getProperty("sql" + s + ".column"));
                        System.out.println("sql["+s+"]=" + sql);
                        //выполняем запрос
                        qr = influxDB.query(new Query(sql, prop.getProperty("influx.database")));
                        //по серии ответа (по сути идём по скриптам, по которым сгруппирован запрос)
                        for (QueryResult.Series sr : qr.getResults().get(0).getSeries()) {

                            String script_json = sr.getTags().get(prop.getProperty("sql" + s + ".script"));
                            String script_metric = "";
                            Double script_value=0.0;

                            //проходимся и находим столбец, указанный для этого запроса в sql.column
                            int vol_i = 0;
                            for (String col : sr.getColumns()) {
                                if (col.equalsIgnoreCase(prop.getProperty("sql" + s + ".column"))) {
                                    //System.out.println("col=" + col);
                                    script_metric = col;
                                    break;
                                }
                                vol_i++;
                            }

                            //парсим значение столбца, который нашли в прошлом шаге
                            for (List<Object> vol : sr.getValues()) {
                                //System.out.println("vol=" + vol.get(vol_i).toString());
                                script_value = Double.parseDouble(vol.get(vol_i).toString());
                            }

                            //System.out.println("\njson=");
                            //System.out.println("script_json=" + script_json);
                            //System.out.println("script_metric=" + script_metric);
                            //System.out.println("script_value=" + script_value);

                            //заполняем значениями json по скрипту
                            JSONObject jo = new JSONObject();
                            jo.put("script", script_json);
                            jo.put("tag", tag);
                            jo.put(script_metric, script_value);

                            //берём sla для скрипта из конфига
                            Double sla = Double.parseDouble(prop.getProperty(script_json + ".sla"));
                            if (sla != null) {
                                jo.put("sla", sla);
                            }

                            //берём профиль для скрипта из конфига
                            String profile_calc = prop.getProperty(script_json + ".profile");
                            //System.out.println("profile_calc="+profile_calc);
                            //System.out.println("profile="+profile);
                            //System.out.println("seconds="+seconds);

                            //расчитываем профиль
                            if (profile_calc != null) {
                                long profile_teor = Math.round(Double.parseDouble(profile_calc) * Double.parseDouble(profile) / 100.0 * seconds);
                                jo.put("profile", profile_teor);
                            }

                            //дополняем значениями из нового запросоа уже существующий json по скрипту
                            boolean found = false;
                            for (int j = 0; j < ja.length(); j++) {
                                JSONObject localjo = ja.getJSONObject(j);
                                if (localjo.getString("script").equals(script_json)) {
                                    ja.getJSONObject(j).put(script_metric, script_value);
                                    found = true;
                                    break;
                                }
                            }
                            //если скрипта не было в массиве - кладём всё
                            if (!found) {
                                ja.put(jo);
                            }
                            //System.out.println("jo=" + jo.toString());
                        }
                        //System.out.println("ja=" + ja.toString());
                        //System.out.println("\nresult=" + qr.getResults().toString());
                    }
                    //закончили все запросы по этому тегу
                    //добавляем в выходной массив
                    for (int j = 0; j < ja.length(); j++) {
                        fullja.put(ja.getJSONObject(j));
                    }

                }//конец по тегу

            }//конец по периоду

            //пишем и сохраняем
            System.out.println("\nResult json=" + fullja.toString());
            file = new FileWriter(prop.getProperty("out"));
            file.write(fullja.toString());
            file.close();
            System.out.println("\nFile saved=" + prop.getProperty("out"));

        } catch (Exception ex) {
            ex.printStackTrace();
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

    public static void ReadProps() {
        try {
            prop.load(new InputStreamReader(Thread.currentThread().getContextClassLoader().getResourceAsStream("config.properties"), Charset.forName("UTF-8")));
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
        String firstTime = args.get("times").get(0).replace(":", "_").replace("-", "_");
        String outdir = args.get("out").get(0);
        String outname = args.get("name").get(0);
        try {
            String folderName = Utils.getFolder();
            Files.createDirectories(Paths.get(outdir + "\\" + folderName));
            String folderFilePath = outdir + "\\" + folderName + "\\" + outname + "_" + firstTime + ".json";
            prop.put("out", folderFilePath);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
