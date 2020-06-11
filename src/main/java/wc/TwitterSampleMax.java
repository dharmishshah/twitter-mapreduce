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
import java.util.StringTokenizer;

public class TwitterSampleMax extends Configured implements Tool {

    private static int  MAX_USER = 100;

    public static void main(String[] args) {
        if (args.length != 3) {
            throw new Error("Three arguments required:\n<input-dir> <output-dir>");
        }

        try {
            ToolRunner.run(new TwitterSampleMax(), args);
        } catch (final Exception e) {
            logger.error("", e);
        }
        System.out.println("running");
    }


    private static final Logger logger = LogManager.getLogger(TwitterSampleMax.class);

    public static class MaxFilterMapper extends Mapper<Object, Text, Text, IntWritable> {
        private final static IntWritable one = new IntWritable(1);
        private final Text word = new Text();

        @Override
        public void setup(Context context){
            Configuration conf = context.getConfiguration();
            String p1 = conf.get("maxcount");
            TwitterSampleMax.MAX_USER = Integer.parseInt(p1);
        }

        @Override
        public void map(final Object key, final Text value, final Context context) throws IOException, InterruptedException {
            final StringTokenizer itr = new StringTokenizer(value.toString());
            while (itr.hasMoreTokens()) {
                String[] values = itr.nextToken().split(",");
                int userFrom = Integer.parseInt(values[0]);
                int userTo = Integer.parseInt(values[1]);
                if(userFrom < MAX_USER && userTo < MAX_USER){
                    word.set(""+userFrom);
                    context.write(word, new IntWritable(userTo));
                }

            }
        }
    }

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

    @Override
    public int run(final String[] args) throws Exception {
        final Configuration conf = getConf();
        conf.set("maxcount",""+Integer.parseInt(args[2]) );
        final Job job = Job.getInstance(conf, "Twitter Triangle Sample");
        job.setJarByClass(TwitterSampleMax.class);
        final Configuration jobConf = job.getConfiguration();
        jobConf.set("mapreduce.output.textoutputformat.separator", ",");
        job.setMapperClass(MaxFilterMapper.class);
        job.setReducerClass(MaxFilterReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        return job.waitForCompletion(true) ? 0 : 1;
    }



}