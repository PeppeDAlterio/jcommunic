package it.unina.sistemiembedded.model;

import it.unina.sistemiembedded.utility.ObjectMapper;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.Optional;

public class ConnectedClient {

    @Getter @Setter
    public static class ConnectedBoard {

        private String name;

        private String serialNumber;

        private boolean inUse = false;

        private boolean debugging = false;

        @Override
        public String toString() {
            return "ConnectedBoard{" +
                    "name='" + name + '\'' +
                    ", serialNumber='" + serialNumber + '\'' +
                    ", inUse=" + inUse +
                    ", debugging=" + debugging +
                    '}';
        }
    }

    @Getter @Setter
    private String ip;

    @Getter @Setter
    private String name;

    @Setter
    private Board board;

    @Getter @Setter
    private Date connectedTimestamp;

    public Optional<ConnectedBoard> getBoard() {
        return Optional.ofNullable(ObjectMapper.map(this.board, ConnectedBoard.class));
    }

}
