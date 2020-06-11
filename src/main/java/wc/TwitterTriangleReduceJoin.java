package wc;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
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

import java.io.IOException;
import java.util.*;

public class TwitterTriangleReduceJoin extends Configured implements Tool {

    public static void main(String[] args) {
        if (args.length != 3) {
            throw new Error("Three arguments required:\n<input-dir> <output-dir> <max-filter>");
        }

        try {
            ToolRunner.run(new TwitterTriangleReduceJoin(), args);
        } catch (final Exception e) {
            logger.error("", e);
        }
        System.out.println("running");
    }


    private static final Logger logger = LogManager.getLogger(TwitterTriangleReduceJoin.class);


    public static class MaxFilterReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
        private final IntWritable result = new IntWritable();

        @Override
        public void reduce(final Text key, final Iterable<IntWritable> values, final Context context) throws IOException, InterruptedException {
            for (final IntWritable val : values) {
                result.set(val.get());
                context.write(key, result);
            }
        }
    }

    public static class Path2Mapper extends Mapper<Object, Text, Text, Text> {
        private final static Text value = new Text("1");
        private final Text word = new Text();

        @Override
        public void map(final Object key, final Text value, final Context context) throws IOException, InterruptedException {
            final StringTokenizer itr = new StringTokenizer(value.toString());
            while (itr.hasMoreTokens()) {
                String valueRead = itr.nextToken();
                String[] values = valueRead.split(",");
                int userFrom = Integer.parseInt(values[0]);
                int userTo = Integer.parseInt(values[1]);
                word.set(""+userFrom);
                value.set(valueRead);
                context.write(word,value);
                word.set(""+userTo);
                value.set(valueRead);
                context.write(word,value);
            }
        }
    }

    public static class Path2Reducer extends Reducer<Text, Text, Text, Text> {
        private final Text result = new Text();

        @Override
        public void reduce(final Text key, final Iterable<Text> values, final Context context) throws IOException, InterruptedException {
            int sum = 0;
            Map<Integer, List<Integer>> pathMap = new HashMap<Integer,List<Integer>>();
            for (final Text val : values) {

                String valueRead = val.toString();
                String[] valuesUser = valueRead.split(",");
                int userFrom = Integer.parseInt(valuesUser[0]);
                int userTo = Integer.parseInt(valuesUser[1]);
                if(pathMap.get(userFrom) == null){
                    pathMap.put(userFrom, new ArrayList<>());
                }
                List<Integer> toUsers = pathMap.get(userFrom);
                toUsers.add(userTo);
                pathMap.put(userFrom,toUsers);
            }

            for(Map.Entry<Integer,List<Integer>> entry:pathMap.entrySet()){
                List<Integer> toUsers = entry.getValue();
                for (Integer v:toUsers){
                    if (pathMap.containsKey(v)){
                       List<Integer> joinedValues = pathMap.get(v);
                        for(Integer u: joinedValues){
                            result.set(entry.getKey()+ "," + v + "," +u);
                            context.write(result, result);
                        }
                   }
                }
            }
        }
    }



    public static class Path3Mapper extends Mapper<Object, Text, Text, Text> {
        private final static Text value = new Text("1");
        private final Text word = new Text();

        @Override
        public void map(final Object key, final Text value, final Context context) throws IOException, InterruptedException {
            final StringTokenizer itr = new StringTokenizer(value.toString());;
            while (itr.hasMoreTokens()) {
                String valueRead = itr.nextToken();
                String[] values = valueRead.split(",");
                int userFrom = -1;
                int userTo = -1;
                if(values.length > 2){
                     userFrom = Integer.parseInt(values[0]);
                     userTo = Integer.parseInt(values[2]);
                }else{
                     userFrom = Integer.parseInt(values[0]);
                     userTo = Integer.parseInt(values[1]);
                }
                word.set(""+userFrom);
                value.set(valueRead);
                context.write(word,value);
                word.set(""+userTo);
                context.write(word,value);
            }
        }
    }

    public static class Path3Reducer extends Reducer<Text, Text, Text, Text> {
        private final Text result = new Text();

        @Override
        public void reduce(final Text key, final Iterable<Text> values, final Context context) throws IOException, InterruptedException {
            Map<Integer, List<Integer>> pathMap2 = new HashMap<Integer,List<Integer>>();
            Map<Integer, List<Integer>> pathMap3 = new HashMap<Integer,List<Integer>>();
            for (final Text val : values) {

                String valueRead = val.toString();
                String[] valuesUser = valueRead.split(",");
                int userFrom = -1;
                int userTo = -1;
                if(valuesUser.length > 2){
                    userFrom = Integer.parseInt(valuesUser[0]);
                    userTo = Integer.parseInt(valuesUser[2]);
                    if(pathMap3.get(userFrom) == null){
                        pathMap3.put(userFrom, new ArrayList<>());
                    }
                    List<Integer> toUsers = pathMap3.get(userFrom);
                    toUsers.add(userTo);
                    pathMap3.put(userFrom,toUsers);

                }else{
                    userFrom = Integer.parseInt(valuesUser[0]);
                    userTo = Integer.parseInt(valuesUser[1]);
                    if(pathMap2.get(userFrom) == null){
                        pathMap2.put(userFrom, new ArrayList<>());
                    }
                    List<Integer> toUsers = pathMap2.get(userFrom);
                    toUsers.add(userTo);
                    pathMap2.put(userFrom,toUsers);

                }
            }

            for(Map.Entry<Integer,List<Integer>> entry:pathMap3.entrySet()){
                if(entry.getKey().equals(Integer.parseInt(key.toString()))){
                    List<Integer> toUsers = entry.getValue();
                    for (Integer v:toUsers) {
                        if (pathMap2.containsKey(v)) {
                            List<Integer> joinedValues = pathMap2.get(v);
                            for (Integer u : joinedValues) {
                                if (u.equals(entry.getKey())) {
                                    result.set("" + key + "," + v);
                                    context.write(new Text("Triangle"), result);
                                }
                            }
                        }
                    }
                }

            }
        }
    }

    public static class TriangleMapperSum extends Mapper<Object, Text, Text, Text> {
        private final static Text value = new Text("1");
        private final Text word = new Text();

        @Override
        public void map(final Object key, final Text value, final Context context) throws IOException, InterruptedException {
            final StringTokenizer itr = new StringTokenizer(value.toString());;
            while (itr.hasMoreTokens()) {
                String valueRead = itr.nextToken();
                String[] values = valueRead.split(",");
                word.set(""+values[0]);
                context.write(word,new Text(valueRead));
            }
        }
    }

    public static class TriangleReducerSum extends Reducer<Text, Text, Text, Text> {
        private final Text result = new Text();

        @Override
        public void reduce(final Text key, final Iterable<Text> values, final Context context) throws IOException, InterruptedException {
            int sum = 0;
            for (final Text val : values) {
                sum+=1;
            }
            sum = sum /3;
            result.set(""+sum);
            logger.info("Number of triangles in Reduce Join - " + sum);
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

        final Job job = Job.getInstance(conf, "Twitter Triangle Sample");
        job.setJarByClass(TwitterTriangleReduceJoin.class);
        final Configuration jobConf = job.getConfiguration();
        jobConf.set("mapreduce.output.textoutputformat.separator", ",");
        job.setMapperClass(Path2Mapper.class);
        //job.setCombinerClass(IntSumReducer.class);
        job.setReducerClass(Path2Reducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        FileInputFormat.addInputPath(job, new Path("filtered"));
        FileOutputFormat.setOutputPath(job, new Path("temp"));
        job.waitForCompletion(true);

        final Job job2 = Job.getInstance(conf, "Twitter Triangle Sample");
        job2.setJarByClass(TwitterTriangleReduceJoin.class);
        final Configuration jobConf2 = job2.getConfiguration();
        jobConf2.set("mapreduce.output.textoutputformat.separator", ",");
        job2.setMapperClass(Path3Mapper.class);
        //job.setCombinerClass(IntSumReducer.class);
        job2.setReducerClass(Path3Reducer.class);
        job2.setOutputKeyClass(Text.class);
        job2.setOutputValueClass(Text.class);
        FileInputFormat.addInputPath(job2, new Path("temp"));
        FileInputFormat.addInputPath(job2, new Path(args[0]));
        FileOutputFormat.setOutputPath(job2, new Path("temp2"));
        job2.waitForCompletion(true);

        final Job job3 = Job.getInstance(conf, "Twitter Triangle Sample");
        job3.setJarByClass(TwitterTriangleReduceJoin.class);
        final Configuration jobConf3 = job3.getConfiguration();
        jobConf3.set("mapreduce.output.textoutputformat.separator", ",");
        job3.setMapperClass(TriangleMapperSum.class);
        //job.setCombinerClass(IntSumReducer.class);
        job3.setReducerClass(TriangleReducerSum.class);
        job3.setOutputKeyClass(Text.class);
        job3.setOutputValueClass(Text.class);
        FileInputFormat.addInputPath(job3, new Path("temp2"));
        FileOutputFormat.setOutputPath(job3, new Path(args[1]));

        return job3.waitForCompletion(true) ? 0 : 1;
    }



}