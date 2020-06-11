package wc;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TwitterTriangleReplicatedJoin extends Configured implements Tool {

    public static void main(String[] args) {
        if (args.length != 3) {
            throw new Error("Three arguments required:\n<input-dir> <output-dir> <max-filter>");
        }

        try {
            ToolRunner.run(new TwitterTriangleReplicatedJoin(), args);
        } catch (final Exception e) {
            logger.error("", e);
        }
        System.out.println("running");
    }


    private static final Logger logger = LogManager.getLogger(TwitterTriangleReplicatedJoin.class);

    public static class ReplicatedJoinMapper extends Mapper<Object, Text, Text, Text> {

        private Map<Integer, List<Integer>> userIdToInfo = new HashMap<Integer, List<Integer>>();

        @Override
        public void setup(Context context) throws IOException,InterruptedException {
            try {

                Configuration conf1 = context.getConfiguration();

                String p1 = conf1.get("data");
                FileSystem fs= FileSystem.get(URI.create(p1),conf1);
                FileStatus[] files = fs.listStatus(new Path (p1));


//                URI[] cacheFiles = context.getCacheFiles();
//                if (cacheFiles == null || cacheFiles.length == 0) {
//                    throw new RuntimeException(
//                            "User information is not set in DistributedCache");
//                }
                // FileSystem fs = FileSystem.get(context.getConfiguration());
                // Read all files in the DistributedCache
                for (FileStatus p : files) {
//                    BufferedReader rdr = new BufferedReader(
//                            new InputStreamReader(fs.open(new Path(p.getPath()))));
                    BufferedReader rdr = new BufferedReader(
                            new InputStreamReader(fs.open(p.getPath())));


//                    BufferedReader rdr = new BufferedReader(
//                            new InputStreamReader(
//                                    new GZIPInputStream(new FileInputStream(
//                                            new File(p.toString())))));

                    String line;
                    // For each record in the user file
                    while ((line = rdr.readLine()) != null) {
                        // Get the user ID for this record
                        String[] temp= line.split(",");
                        int userFrom = Integer.parseInt(temp[0]);
                        int userTo = Integer.parseInt(temp[1]);

                        if(userIdToInfo.get(userFrom) == null){
                            userIdToInfo.put(userFrom, new ArrayList<>());
                        }
                        List<Integer> toUsers = userIdToInfo.get(userFrom);
                        toUsers.add(userTo);
                        userIdToInfo.put(userFrom,toUsers);
                    }
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void map(final Object key, final Text value, final Context context) throws IOException, InterruptedException {
            String[] temp  = value.toString().split(",");
            int userFrom = Integer.parseInt(temp[0]);
            int userTo = Integer.parseInt(temp[1]);
            if (userIdToInfo.containsKey(userTo)) {
                List<Integer> matches = userIdToInfo.get(userTo);
                for (Integer item : matches) {
                    String tp = temp[0] + "," + temp[1] + "," + item;
                    int path2From = userFrom;
                    int path2To =  item;
                    if(userIdToInfo.containsKey(path2To)){
                        List<Integer> matchesPath2 = userIdToInfo.get(path2To);
                        for (Integer itemPath2 : matchesPath2) {
                            if(itemPath2 == path2From){
                                String output = "" + path2From + "," + path2To;
                                context.write(new Text("Triangle"), new Text(output));
                            }

                        }
                    }
                }
            }
        }
    }

    public static class ReplicatedJoinReducer extends Reducer<Text, Text, Text, Text> {
        private final Text result = new Text();

        @Override
        public void reduce(final Text key, final Iterable<Text> values, final Context context) throws IOException, InterruptedException {
            int sum = 0;
            for (final Text val : values) {
                sum+=1;
            }
            sum = sum /3;
            result.set(""+sum);
            logger.info("Number of triangles in Replicated Join - " + sum);
            context.write(new Text("No of Triangle"),result);
        }
    }


    @Override
    public int run(final String[] args) throws Exception {

        final Configuration conf = getConf();
        conf.set("maxcount",""+Integer.parseInt(args[2]) );
        final Job maxjobfilter = Job.getInstance(conf, "Twitter Triangle Sample");
        maxjobfilter.setJarByClass(TwitterSampleMax.class);
        final Configuration jobConfMax = maxjobfilter.getConfiguration();
        jobConfMax.set("mapreduce.output.textoutputformat.separator", ",");
        maxjobfilter.setMapperClass(TwitterSampleMax.MaxFilterMapper.class);
        maxjobfilter.setReducerClass(TwitterSampleMax.MaxFilterReducer.class);
        maxjobfilter.setOutputKeyClass(Text.class);
        maxjobfilter.setOutputValueClass(IntWritable.class);
        FileInputFormat.addInputPath(maxjobfilter, new Path(args[0]));
        FileOutputFormat.setOutputPath(maxjobfilter, new Path("filtered"));
        maxjobfilter.waitForCompletion(true);


        conf.set("data",new Path("filtered").toString());
        final Job job = Job.getInstance(conf, "Twitter Triangle");
        job.setJarByClass(TwitterTriangleReplicatedJoin.class);
        final Configuration jobConf = job.getConfiguration();
        jobConf.set("mapreduce.output.textoutputformat.separator", ",");
        job.setMapperClass(ReplicatedJoinMapper.class);
        job.setReducerClass(ReplicatedJoinReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        FileInputFormat.addInputPath(job, new Path("filtered"));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));

        // adding input files as distributed cache files
//        FileSystem fs= FileSystem.get(new Path(args[0]).toUri(),conf);
//        FileStatus[] files = fs.listStatus(new Path(args[0]));
//        for(FileStatus f: files){
//            job.addCacheFile(f.getPath().toUri() );
//        }


        return job.waitForCompletion(true) ? 0 : 1;
    }



}