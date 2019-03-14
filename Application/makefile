JFLAGS = -g
JC = javac

default: classes

classes: clean
        $(JC) $(JFLAGS) ./src/main/java/peerProcess/*.java
        mkdir peerProcess
        cp ./src/main/java/peerProcess/* peerProcess
        cp *.cfg peerProcess
        cp *.dat peerProcess

clean:
        rm -rf ./src/main/java/peerProcess/*.class
        rm -rf peerProcess
