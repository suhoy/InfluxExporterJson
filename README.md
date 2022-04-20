# InfluxExporterJson
Export influx data to json  
Part of [data collector](https://github.com/suhoy/TestUploader) for [load testing hub](https://github.com/suhoy/LoadTestingHubPublic)   
Works with influx v1.8 and java 8   

### Arguments
```java
-config     Path to config file  
-times      Start of time periods, from 1 to N  
-durations  Periods durations, could be 1 or N  
-profiles   Profiles in %, could be 1 or N  
-out        Path to output data (absolute or relative) 
-name       Output file name (date and time will be added)  
```

### Start example
```java
java -jar InfluxExporterJson-1.0.jar -config C:\example\config.txt -out C:\example\out -times 2022-01-12T12:00:00 -durations 24:00:00 -profiles 80
or
java -jar InfluxExporterJson-1.0.jar -config config.txt -out .\out -name example2 -times 2022-01-12T20:55:20 2022-01-12T21:01:20 -durations 00:05:00 -profiles 100 200
or  
java -jar InfluxExporterJson-1.0.jar -config config.txt -out .\out -name example3 -times 2022-01-10T23:55:00 2022-01-11T23:55:00 2022-01-12T23:55:00 -durations 00:30:00 01:00:00 01:30:00 -profiles 150.5
```  
  
  
### Config example  
```properties
#connection data
influx.url=http://localhost:8086
influx.user=admin
influx.password=admin
influx.database=dbname

#connection data
influx.url=http://localhost:8086
influx.user=admin
influx.password=admin
influx.database=jrunner

#query should be grouped by one tag and returning one field
#pass_count, fail_count, pass_time and fail_time are constants

sql1.type=pass_count
sql1.query=SELECT count("count") as "pass_count" FROM "dbname"."autogen"."tablename" WHERE "status" = 'true' and  time > '__start__' AND time < '__finish__' GROUP BY "script"

sql2.type=fail_count
sql2.query=SELECT count("count") as "fail_count" FROM "dbname"."autogen"."tablename" WHERE "status" = 'false' and  time > '__start__' AND time < '__finish__' GROUP BY "script"

sql3.type=pass_time
sql3.query=SELECT (percentile("resp", 99.5))/1000 as "pass_time" FROM "dbname"."autogen"."tablename" WHERE "status" = 'true' and  time > '__start__' AND time < '__finish__' GROUP BY "script"
sql3.tag=99.5%

sql4.type=fail_time
sql4.query=SELECT (percentile("resp", 99.5))/1000 as "fail_time" FROM "dbname"."autogen"."tablename" WHERE "status" = 'false' and  time > '__start__' AND time < '__finish__' GROUP BY "script"
sql4.tag=99.5%

sql5.type=pass_time
sql5.query=SELECT (percentile("resp", 99.9))/1000 as "pass_time" FROM "dbname"."autogen"."tablename" WHERE "status" = 'true' and  time > '__start__' AND time < '__finish__' GROUP BY "script"
sql5.tag=99.9%

sql6.type=fail_time
sql6.query=SELECT (percentile("resp", 99.9))/1000 as "fail_time" FROM "dbname"."autogen"."tablename" WHERE "status" = 'false' and  time > '__start__' AND time < '__finish__' GROUP BY "script"
sql6.tag=99.9%

#scripts list, profile in tps 100%, scripts names should be unique
# "s1" - parent script, it has 1 child - "c1"

s1.name=Correlation_Challenge_Mod
s1.tps=2
s1.sla=1.5

s1.c1.name=ex0
s1.c1.tps=4
s1.c1.sla=2.5
```

### Output example
```json
[
	{
		"time_start": "2022-01-12T20:55",
		"time_finish": "2022-01-12T21:00",
		"profile": 100,
		"about": "",
		"stats": [
			{
				"script": "Correlation_Challenge_Mod",
				"sla": 1.5,
				"profile": 600,
				"child_list": [
					{
						"script": "ex0",
						"sla": 2.5,
						"profile": 1200,
						"pass_count": 1199,
						"stat_time": [
							{
								"tag": "99.5%",
								"pass_time": 2.499
							},
							{
								"tag": "99.9%",
								"pass_time": 2.5
							}
						]
					}
				],
				"pass_count": 478,
				"fail_count": 120,
				"stat_time": [
					{
						"tag": "99.5%",
						"pass_time": 1.867,
						"fail_time": 1.827
					},
					{
						"tag": "99.9%",
						"pass_time": 1.992,
						"fail_time": 1.957
					}
				]
			}
		],
		"tps": 2
	},
	{
		"time_start": "2022-01-12T21:01",
		"time_finish": "2022-01-12T21:06",
		"profile": 200,
		"about": "",
		"stats": [
			{
				"script": "Correlation_Challenge_Mod",
				"sla": 1.5,
				"profile": 1200,
				"child_list": [
					{
						"script": "ex0",
						"sla": 2.5,
						"profile": 2400,
						"pass_count": 2403,
						"stat_time": [
							{
								"tag": "99.5%",
								"pass_time": 2.498
							},
							{
								"tag": "99.9%",
								"pass_time": 2.5
							}
						]
					}
				],
				"pass_count": 987,
				"fail_count": 210,
				"stat_time": [
					{
						"tag": "99.5%",
						"pass_time": 1.861,
						"fail_time": 1.738
					},
					{
						"tag": "99.9%",
						"pass_time": 2.319,
						"fail_time": 2.218
					}
				]
			}
		],
		"tps": 4
	}
]
```
