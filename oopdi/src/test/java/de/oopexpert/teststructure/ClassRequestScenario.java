package de.oopexpert.teststructure;

import de.oopexpert.oopdi.annotation.InjectInstance;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassRequestScenario {

    @InjectInstance
    private ClassRequestState state;

    @InjectInstance
    private ClassRequestReader reader;

    public Result execute(int value, boolean fail) {
        state.setValue(value);
        int readFromReader = reader.readValue();
        int idInScenario = state.getId();
        int idInReader = reader.readStateId();

        if (fail) {
            throw new IllegalStateException("forced failure");
        }

        return new Result(idInScenario, idInReader, readFromReader);
    }

    public static class Result {
        private final int idInScenario;
        private final int idInReader;
        private final int readFromReader;

        public Result(int idInScenario, int idInReader, int readFromReader) {
            this.idInScenario = idInScenario;
            this.idInReader = idInReader;
            this.readFromReader = readFromReader;
        }

        public int getIdInScenario() {
            return idInScenario;
        }

        public int getIdInReader() {
            return idInReader;
        }

        public int getReadFromReader() {
            return readFromReader;
        }
    }

}
