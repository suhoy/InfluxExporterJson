package exporter.influx;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;

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

    /*
//путь до создаваемого файла
public static String folderFilePath;
//агумент. общая директория сохранения
public static String out;
//агумент. название создаваемого репорта
public static String name;
//конфиг
public static Properties props;
//агумент. подробная печать
public static boolean debug;
//агумент. расположение конфига
public static String config;
//агумент. длина периодов
public static String[] durations;
//агумент. начала периодов
public static String[] times;
//агумент. % нагрузки
public static String[] profiles;
*/
    public static void main(String[] arg) {
        try {
            //читаем аргументы
            ReadParams(arg);
            //читаем конфиг

            ReadProps();
            //AnalyzeParams();


            //props = new Properties();
            //props.load(new FileInputStream(config));

            //создаем директорию под json
            CreatefolderFilePath();


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
                    System.out.println("\n" + h + "." + t + ") Tag:");
                    System.out.println("tag.name=" + prop.getProperty("tag" + t + ".name"));
                    System.out.println("tag.count_function=" + prop.getProperty("tag" + t + ".count_function"));
                    System.out.println("tag.time_function=" + prop.getProperty("tag" + t + ".time_function"));


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

                    //под разные запросы
                    String querytype;
                    String functype;

                    //вставить запрос на получение списка операций
                    //потом создать json-ов и наполнять его
                    //потом добавить json во внешний

                    //заполняем запрос #sql pass count
                    querytype="pass_count";
                    functype="count_function";
                    if (Boolean.parseBoolean(prop.getProperty("sql."+querytype+".enabled"))) {
                        String sql = prop.getProperty("sql."+querytype+".query");
                        sql = sql.replaceAll("__start__", strCurrentStart);
                        sql = sql.replaceAll("__finish__", strCurrentFinish);
                        sql = sql.replaceAll("__function__", prop.getProperty("tag" + t + "."+functype));
                        System.out.println("\nsql="+sql);
                        qr = influxDB.query(new Query(sql, prop.getProperty("influx.database")));
                        //qr.getResults().get(0).getSeries().get(0).
                        System.out.println("result=" + qr.getResults().toString());
                    }

                    querytype="fail_count";
                    functype="count_function";
                    if (Boolean.parseBoolean(prop.getProperty("sql."+querytype+".enabled"))) {
                        String sql = prop.getProperty("sql."+querytype+".query");
                        sql = sql.replaceAll("__start__", strCurrentStart);
                        sql = sql.replaceAll("__finish__", strCurrentFinish);
                        sql = sql.replaceAll("__function__", prop.getProperty("tag" + t + "."+functype));
                        System.out.println("\nsql="+sql);
                        qr = influxDB.query(new Query(sql, prop.getProperty("influx.database")));
                        System.out.println("result=" + qr.getResults().toString());
                    }

                    querytype="pass_time";
                    functype="time_function";
                    if (Boolean.parseBoolean(prop.getProperty("sql."+querytype+".enabled"))) {
                        String sql = prop.getProperty("sql."+querytype+".query");
                        sql = sql.replaceAll("__start__", strCurrentStart);
                        sql = sql.replaceAll("__finish__", strCurrentFinish);
                        sql = sql.replaceAll("__function__", prop.getProperty("tag" + t + "."+functype));
                        System.out.println("\nsql="+sql);
                        qr = influxDB.query(new Query(sql, prop.getProperty("influx.database")));
                        System.out.println("result=" + qr.getResults().toString());
                    }

                    querytype="fail_time";
                    functype="time_function";
                    if (Boolean.parseBoolean(prop.getProperty("sql."+querytype+".enabled"))) {
                        String sql = prop.getProperty("sql."+querytype+".query");
                        sql = sql.replaceAll("__start__", strCurrentStart);
                        sql = sql.replaceAll("__finish__", strCurrentFinish);
                        sql = sql.replaceAll("__function__", prop.getProperty("tag" + t + "."+functype));
                        System.out.println("\nsql="+sql);
                        qr = influxDB.query(new Query(sql, prop.getProperty("influx.database")));
                        System.out.println("result=" + qr.getResults().toString());
                    }


                }


            }






            /*
            //Переменная, куда будем сохранять результаты всех запросов
            Data allData = new Data();


            //по количеству sql запросов из конфига
            for (int h = 1; h <= Integer.parseInt(props.getProperty("sql.count")); h++) {
                ArrayList<String[][]> durationsResults = new ArrayList();
                //по количеству периодов
                for (int y = 0; y < times.length; y++) {
                    //берём данные для текущего периода
                    String time = times[y];
                    String duration = "";
                    String profile = "";
                    //если указан 1 duration - берём его для всех периодов
                    if (times.length == durations.length) {
                        duration = durations[y];
                    } else {
                        duration = durations[0];
                    }
                    //если указан 1 profiles - берём его для всех периодов
                    if (times.length == profiles.length) {
                        profile = profiles[y];
                    } else {
                        profile = profiles[0];
                    }
                    Utils.debugMessage("\r\nperiod" + (y + 1) + "=\t" + time + " + " + duration);
                    //хттп клиент
                    OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient().newBuilder()
                            .connectTimeout(40, TimeUnit.SECONDS)
                            .readTimeout(60, TimeUnit.SECONDS)
                            .writeTimeout(60, TimeUnit.SECONDS);

                    //коннект к инфлюксу
                    influxDB = InfluxDBFactory.connect(props.getProperty("influx.url"), props.getProperty("influx.user"), props.getProperty("influx.password"), okHttpClientBuilder);

                    //создаем start и finish в utc
                    String strCurrentStart = Utils.convertToUTC(time);
                    String strCurrentFinish = Utils.convertToUTC(Utils.sumTime(time, duration));

                    //заполняем запрос
                    String sql = props.getProperty("sql" + h + ".query");
                    sql = sql.replaceAll("__start__", strCurrentStart);
                    sql = sql.replaceAll("__finish__", strCurrentFinish);

                    //запрос и ответ из инфлюкса
                    Utils.debugMessage("sql" + h + ".query=\t" + sql);
                    qr = influxDB.query(new Query(sql, props.getProperty("influx.database")));
                    Utils.debugMessage("sql" + h + ".result=\t" + qr.getResults().toString());

                    //парсинг ответа на один запрос и занесение в список данных
                    durationsResults.add(Utils.Parse(qr, Utils.InfluxTimeToXlsxTime(strCurrentStart), Utils.InfluxTimeToXlsxTime(strCurrentFinish), duration, profile));
                }
                allData.add(props.getProperty("sql" + h + ".sheet"), durationsResults);
            }
            //пишем в эксель
            Report r = new Report(props.getProperty("xlsx.template_path"), folderFilePath, allData);
*/
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
        System.out.println("\nStarted with args:\n");
        for (Map.Entry<String, List<String>> entry : args.entrySet()) {
            System.out.println(entry.getKey() + ":" + entry.getValue());
        }
    }

    public static void ReadProps() {
        try {
            prop.load(new InputStreamReader(Thread.currentThread().getContextClassLoader().getResourceAsStream("config.properties"), Charset.forName("UTF-8")));
            System.out.println("\nGet config, unsorted:\n");
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
/*
    //чтение агрументов
    public static void AnalyzeParams() {
        config = String.valueOf(args.get("config").get(0));
        name = String.valueOf(args.get("name").get(0));
        debug = Boolean.parseBoolean(args.get("debug").get(0));
        out = String.valueOf(args.get("out").get(0));

        durations = new String[args.get("durations").size()];
        for (int i = 0; i < args.get("durations").size(); i++) {
            durations[i] = String.valueOf(args.get("durations").get(i));
        }
        times = new String[args.get("times").size()];
        for (int i = 0; i < args.get("times").size(); i++) {
            times[i] = String.valueOf(args.get("times").get(i));
        }

        profiles = new String[args.get("profiles").size()];
        for (int i = 0; i < args.get("profiles").size(); i++) {
            profiles[i] = String.valueOf(args.get("profiles").get(i));
        }
        printArgs();
    }

    //вывод аргументов
    public static void printArgs() {
        System.out.println("Arguments:\r\n");
        System.out.println("Config:\t" + config);
        System.out.println("Out:\t" + out);
        System.out.println("Name:\t" + name);
        System.out.println("Debug:\t" + debug);
        System.out.println("Durations:\t" + Arrays.toString(durations));
        System.out.println("Profiles:\t" + Arrays.toString(profiles));
        System.out.println("Periods:\t" + Arrays.toString(times));
    }

    static String readFile(String path, Charset encoding)
            throws IOException
    {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding);
    }
*/
}
