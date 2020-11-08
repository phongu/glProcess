package map;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.system.Callback;
import java.util.Iterator;
import util.DemoUtils;

import java.io.IOException;
import java.util.ArrayList;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43C.*;
import static org.lwjgl.system.MemoryUtil.*;

//    Passing textures to and from the GPU is very slow, doing so each time we want to
//    run a filter would negate any performance benefits, unless the filter is very expensive.
//    Ideally we can reach a point where everything is done on the GPU and then downloaded.

//
//          gl = new glProcess();
//          gl.upload(FloatMask1).doSomething().doSomethingElseWith( gl.upload(floatMask2).doThing() ).save();

//          ...
//          gl.done();

public strictfp class glProcess {

    private class Texture {
        public int ID;
        public final int internalFormat;
        public final int format;
        Texture() {
            this.ID = 0;
            this.internalFormat = GL_R16;
            this.format = GL_RED;
        }
        Texture(int ID, int internalFormat, int format) {
            this.ID = ID;
            this.internalFormat = internalFormat;
            this.format = format;
        }
    }


    //      tasks are deferred operations that are scheduled by methods on glMask and
    //      run once glProcess.done is called
    private strictfp class Task {
        public final glProcess gl;
        public final glMask thisMask;
        public Texture[] sources = new Texture[1];
        public Texture destination = new Texture();
        public Task(glProcess gl, glMask thisMask) {this.gl = gl;this.thisMask = thisMask;}
        public void run() throws InterruptedException {}
        public void retire() {
            if (this.sources[0] != null)
                glDeleteTextures(this.sources[0].ID);
            if (thisMask.tasks.size() > 1) {
                Task nextTask = thisMask.tasks.get(1);
                nextTask.sources[0] = this.destination;
            }
        }
    }
    private strictfp class UploadTask extends Task {
        private final float[] pixels;
        public UploadTask(glProcess gl, glMask thisMask, float[] pixels) {
            super(gl, thisMask);
            this.pixels = pixels;
        }
        @Override
        public void run() {
            destination.ID = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, destination.ID);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R16, thisMask.size, thisMask.size, 0, GL_RED, GL_FLOAT, pixels);
            glBindTexture(GL_TEXTURE_2D, 0);
            System.out.println(
                    "glMask " + thisMask.name +
                    " uploaded to tex ID " +
                            destination.ID
            );
        }
    }
    private strictfp class BlurTask extends Task  {
        private final Float radius;

        public BlurTask(glProcess gl, glMask thisMask, Float radius) {
            super(gl, thisMask);
            this.radius = radius;
        }

        @Override
        public void run() throws InterruptedException {
            prepareDestination(destination, thisMask.size);
            System.out.println(
                "blurring glMask " + thisMask.name +
                ", radius " + radius
            );

            glAttachShader(program, blurShader);
            glLinkProgram(program);
            glUseProgram(program);
            prepareDestination(destination, thisMask.size);
            glBindTexture(GL_TEXTURE_2D, sources[0].ID);
            int textureSize = glGetUniformLocation(program, "textureSize");
            glUniform1f(textureSize, thisMask.size);
            int uRadius = glGetUniformLocation(program, "radius");
            glUniform1f(uRadius, radius);
            int direction = glGetUniformLocation(program, "direction");
            glUniform2f(direction, 0, 1);
            glViewport(0, 0, thisMask.size, thisMask.size);
            glDrawArrays(GL_TRIANGLES, 0, 3);

            glBindTexture(GL_TEXTURE_2D, 0);
            glDeleteTextures(sources[0].ID);
            sources[0] = destination;
            destination = new Texture();
            prepareDestination(destination, thisMask.size);
            glBindTexture(GL_TEXTURE_2D, sources[0].ID);

            glUniform2f(direction, 1, 0);
            glDrawArrays(GL_TRIANGLES, 0, 3);

            glDetachShader(program, blurShader);
        }
    }
    private strictfp class DistanceField extends Task {
        float maxNormalizedDistance;

        DistanceField(glProcess gl, glMask thisMask, float maxNormalizedDistance) {
            super(gl, thisMask);
            sources[0] = new Texture();
            this.maxNormalizedDistance = StrictMath.min(maxNormalizedDistance, 1);
            destination = new Texture(0, GL_RGBA32F, GL_RGBA);
        }
        @Override
        public void run() {

            System.out.println(
                "glMask " + thisMask.name +
                " running DistanceField first pass"
            );

            glAttachShader(program, distanceFieldFirstPassShader);
            glLinkProgram(program);
            glUseProgram(program);
            prepareDestination(destination, thisMask.size);
            glBindTexture(GL_TEXTURE_2D, sources[0].ID);
            glViewport(0, 0, thisMask.size, thisMask.size);
            glDrawArrays(GL_TRIANGLES, 0, 3);
            glDetachShader(program, distanceFieldFirstPassShader);


            glAttachShader(program, distanceFieldPassNShader);
            glLinkProgram(program);
            glUseProgram(program);

            for (int pow2 = (int) (
                    StrictMath.ceil(
                        StrictMath.log((float)thisMask.size * maxNormalizedDistance)
                        / StrictMath.log(2)
                    )
                );
                 pow2>=0;
                 pow2--
            ) {
                float offset = (float) StrictMath.pow(2, pow2) / thisMask.size;
                System.out.println(
                        "glMask " + thisMask.name +
                                " running DistanceField pass " + (pow2) +
                                " with offset " + offset
                );

                glBindTexture(GL_TEXTURE_2D, 0);
                glDeleteTextures(sources[0].ID);
                sources[0] = destination;
                destination = new Texture(0, GL_RGBA32F, GL_RGBA);
                prepareDestination(destination, thisMask.size);
                glBindTexture(GL_TEXTURE_2D, sources[0].ID);

                int uOffset = glGetUniformLocation(program, "offset");
                glUniform1f(uOffset, offset);
                glDrawArrays(GL_TRIANGLES, 0, 3);

            }
            glDetachShader(program, distanceFieldPassNShader);

            System.out.println(
                "glMask " + thisMask.name +
                " running DistanceField last pass"
            );

            glAttachShader(program, distanceFieldLastPassShader);
            glLinkProgram(program);
            glUseProgram(program);

            glBindTexture(GL_TEXTURE_2D, 0);
            glDeleteTextures(sources[0].ID);
            sources[0] = destination;
            destination = new Texture();
            prepareDestination(destination, thisMask.size);
            glBindTexture(GL_TEXTURE_2D, sources[0].ID);
            int uMaxNormalizedDistance = glGetUniformLocation(program, "maxDistance");
            glUniform1f(uMaxNormalizedDistance, maxNormalizedDistance);
            glDrawArrays(GL_TRIANGLES, 0, 3);


            glDetachShader(program, distanceFieldLastPassShader);
        }
    }

    private strictfp class BlendTask extends Task {
        public boolean invertBlendMask = false;

        BlendTask(glProcess gl, glMask thisMask, glMask otherMask, glMask alphaMask, boolean invertBlendMask) {
            super(gl, thisMask);
            this.invertBlendMask = invertBlendMask;
        }
        @Override
        public void run() {

            System.out.println(
                "glMask " + thisMask.name +
                "running blend pass"
            );

        }
    }
    private strictfp class ReadTask extends Task {
        Task reader;
        int sourceSlot;
        ReadTask(glProcess gl, glMask thisMask, Task reader, int sourceSlot) {
            super(gl, thisMask);
            this.reader = reader;
            this.sourceSlot = sourceSlot;
        }
        @Override
        public void run() {
            reader.sources[sourceSlot] = sources[0];
            System.out.println(
                "glMask " + thisMask.name +
                "is ready to be read by task " +
                reader.getClass().toString() +
                "on glMask " +
                reader.thisMask.name
            );
        }
    }
    private strictfp class SaveTask extends Task {
        SaveTask(glProcess gl, glMask thisMask) {
            super(gl, thisMask);
        }
        @Override
        public void run() {
            glBindTexture(GL_TEXTURE_2D, sources[0].ID);
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RED_INTEGER, GL_SHORT, thisMask.sourceFloatMask.mask);
            glBindTexture(GL_TEXTURE_2D, 0);
            System.out.println(
                    "glMask " + thisMask.name +
                    "has been saved to FloatMask " +
                    thisMask.sourceFloatMask.name
            );
        }
    }



    //      glMask is used to schedule mask creation, upload, processing and readback on the GPU.
    private strictfp class glMask {
        private final glProcess gl;
        private final FloatMask sourceFloatMask;
        public final int size;
        public final String name;
        private final ArrayList<Task> tasks = new ArrayList<Task>();

        public glMask(glProcess gl, FloatMask floatMask) {
            this.sourceFloatMask = floatMask;
            size = floatMask.size;
            this.name = floatMask.name;
            this.gl = gl;
            gl.masks.add(this);
            tasks.add(new UploadTask(gl, this, floatMask.mask));
            System.out.println("made glMask " + name);
            System.out.println(
                    "added Upload task to glMask " + name +
                    " with source " + floatMask.name
            );
        }
        public glMask blur(Float radius) {
            tasks.add( new BlurTask(gl, this, 2*radius) );
            System.out.println("added blur task to glMask " + name);
            return this;
        }
        public glMask blend(glMask otherMask, glMask alphaMask, boolean invertBlendMask) {
            BlendTask blendTask = new BlendTask(gl, this, otherMask, alphaMask, invertBlendMask);
            tasks.add(blendTask);
            System.out.println("added blend task to mask " + name);
            otherMask.tasks.add(new ReadTask(gl, otherMask, blendTask, 1));
            System.out.println("added Read task to glMask " + otherMask.name);
            alphaMask.tasks.add(new ReadTask(gl, alphaMask, blendTask, 2));
            System.out.println("added Read task to glMask " + alphaMask.name);
            return this;
        }
        public glMask toDistanceField() {
            return this.toDistanceField(1);
        }
        public glMask toDistanceField(float maxNormalizedDistance) {
            int passes;
            this.tasks.add(new DistanceField(gl, this, maxNormalizedDistance));
            return this;
        }
        public glMask save() {
            tasks.add(new SaveTask(gl, this));
            System.out.println("added save task to mask " + name);
            return this;
        }

    }
    public glMask upload(FloatMask mask) {
        return new glMask(this, mask);
    }


    private final GLFWErrorCallback errCallback;
    private final Callback debugProc;
    private int previewSize = 512;
    public final long window;
    private final ArrayList<glMask> masks = new ArrayList<glMask>();
    private final ArrayList<Texture> allocatedTextures = new ArrayList<Texture>();
    private final int program;
    private final int blitShader;
    private final int blurShader;
    private final int distanceFieldFirstPassShader;
    private final int distanceFieldPassNShader;
    private final int distanceFieldLastPassShader;
    private final int fbo; //framebuffer object
//    private int presentColumns = 4;


    public glProcess() throws IOException {

        glfwSetErrorCallback(errCallback = GLFWErrorCallback.createPrint(System.err));
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation

        window = glfwCreateWindow(previewSize, previewSize, "glMapGenerator", NULL, NULL); // Create the window
        if (window == NULL) throw new RuntimeException("Failed to create the GLFW window");
        glfwMakeContextCurrent(window);
        glfwSwapInterval(10);
        GLCapabilities caps = GL.createCapabilities();
        if (!caps.GL_EXT_framebuffer_object)
            throw new AssertionError("This demo requires the EXT_framebuffer_object extension");
        debugProc = GLUtil.setupDebugMessageCallback();
        glfwShowWindow(window);

        program = glCreateProgram();
        glAttachShader(program, DemoUtils.createShader("shaders/quad.vs.glsl", GL_VERTEX_SHADER));
        glBindFragDataLocation(program, 0, "color");
        glLinkProgram(program);
        glBindVertexArray(glGenVertexArrays());
        fbo = glGenFramebuffers();

        blitShader = DemoUtils.createShader("shaders/blit.fs.glsl", GL_FRAGMENT_SHADER);
        blurShader = DemoUtils.createShader("shaders/blur.fs.glsl", GL_FRAGMENT_SHADER);
        distanceFieldFirstPassShader = DemoUtils.createShader("shaders/distanceFieldFirstPass.fs.glsl", GL_FRAGMENT_SHADER);
        distanceFieldPassNShader = DemoUtils.createShader("shaders/distanceFieldPassN.fs.glsl", GL_FRAGMENT_SHADER);
        distanceFieldLastPassShader = DemoUtils.createShader("shaders/distanceFieldLastPass.fs.glsl", GL_FRAGMENT_SHADER);
    }

    //      since AWS and glfw don't play nice together, this is a very rough Visualiser equivalent
    //      needs some way to draw text.
    private void present(Task task) {
        System.out.println("presenting task" + task);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glAttachShader(program, blitShader);
        glLinkProgram(program);
        glUseProgram(program);
        glBindTexture(GL_TEXTURE_2D, task.destination.ID);
        glViewport(0, 0, previewSize, previewSize);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glDetachShader(program, blitShader);
        glfwSwapBuffers(window);
    }
    private void prepareDestination(Texture destination, int size) {
        destination.ID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, destination.ID);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexStorage2D(GL_TEXTURE_2D, 1, destination.internalFormat, size, size);
        glBindTexture(GL_TEXTURE_2D, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, destination.ID, 0);
    }
    private void done() throws InterruptedException {

        while(masks.size() > 0){ //while there are masks
            masks:
            for (Iterator<glMask> iMask = masks.iterator(); iMask.hasNext();) {

                glMask mask = iMask.next();
                System.out.println("processing mask " + mask.name);

                for (Iterator<Task> iTask = mask.tasks.iterator(); iTask.hasNext();) {
                    Task task = iTask.next();
                    System.out.println("processing task " + task);
                    boolean sourcesReady = true;

                    if(task.getClass() != UploadTask.class) {
                        sources:
                        for (Texture tex : task.sources) {
                            if (tex.ID == 0) {
                                System.out.println("sources not ready");
                                sourcesReady = false;
                                break sources;
                            }
                        }
                    }
                    if (!sourcesReady) continue masks;
                    else {
                        System.out.println("running task " + task);
                        task.run();
                        if (task.getClass() != ReadTask.class) {
                            present(task);
                            Thread.sleep(1000);
                            System.out.println("retiring task " + task);
                            task.retire();
                            iTask.remove();
                        }
                    }
                }
                if (mask.tasks.size() == 0) {
                    iMask.remove();
                    System.out.println("mask complete " + mask.name + ", remaining masks: " + masks.size());
                }
            }
        }
        System.out.println("done");
//        if (debugProc != null)
//            debugProc.free();
//        errCallback.free();
//        glfwTerminate();
    }
    

    public static void main(String[] args) throws Exception {

        FloatMask blobs1 = new FloatMask(512, "BLOBS1");
        FloatMask blobs2 = new FloatMask(512, "BLOBS2");
        glProcess mapGen = new glProcess();
        glMask glBlobs1 = mapGen.upload(blobs1).toDistanceField(.3f);
        glMask glBlobs2 = mapGen.upload(blobs2).blur(50f);

        mapGen.done();

        while (!glfwWindowShouldClose(mapGen.window)) {
            glfwPollEvents();
        }
        glfwTerminate();
    }
}
