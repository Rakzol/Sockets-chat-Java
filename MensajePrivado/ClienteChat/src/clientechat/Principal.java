package clientechat;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * @author Carlos Gilberto Jaime Rosas.
 */
public class Principal {

    public static void main(String[] args) {

        try {
            new Cliente(new Socket(args[0], Integer.valueOf(args[1])));
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.err.println("No se agregó la cantidad de argumentos necesarios:\n"
                    + "1 .- Dirección IPv4 del servidor.\n"
                    + "2 .- Número de puero del servidor en el rango de 0 a 65535.");
            System.exit(1);
        } catch (UnknownHostException ex) {
            System.err.println("Dirección IPv4 Invalida.");
            System.exit(1);
        } catch (ConnectException ex) {
            System.err.println("Tiepo para la conexión agotado o conexión rechazada.");
            System.exit(1);
        } catch (NumberFormatException ex) {
            System.err.println("El puerto tiene que ser un número entero.");
            System.exit(1);
        } catch (IllegalArgumentException ex) {
            System.err.println("El puerto tiene que estar en el rango de 0 a 65535.");
            System.exit(1);
        } catch (Exception ex) {
            System.err.println("No se puede conectar con el servidor.");
            System.exit(1);
        }

    }

    private static class Cliente {

        private Socket socket;
        private DataInputStream lector;
        private DataOutputStream escritor;
        private JFrame ventana = new JFrame("Conectando. . .");
        private JTextArea areaTexto = new JTextArea(25, 50);
        private JScrollPane scrollAreaTexto = new JScrollPane(areaTexto);
        private JTextField campoTexto = new JTextField(50);

        private Cliente(Socket socket) {
            this.socket = socket;
            areaTexto.setEditable(false);

            campoTexto.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        if (campoTexto.getText().startsWith("/")) {
                            escritor.writeUTF(campoTexto.getText());
                        } else {
                            escritor.writeUTF("mensaje" + campoTexto.getText());
                        }
                        campoTexto.setText("");
                    } catch (IOException ex) {
                    }
                }
            });

            ventana.add(scrollAreaTexto, BorderLayout.CENTER);
            ventana.add(campoTexto, BorderLayout.SOUTH);
            ventana.pack();
            ventana.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            ventana.setVisible(true);

            principal();
        }

        private void principal() {

            try {
                lector = new DataInputStream(socket.getInputStream());
                escritor = new DataOutputStream(socket.getOutputStream());
            } catch (Exception ex) {
            }

            while (true) {
                String lectura;
                try {
                    lectura = lector.readUTF();
                } catch (UTFDataFormatException ex) {
                    lectura = "";
                } catch (Exception ex) {
                    agregar("Se perdio la conexión con el servidor.");
                    cerrar();
                    return;
                }

                if (lectura.startsWith("nombreAceptado")) {
                    areaTexto.setText("");
                    ventana.setTitle(lectura.substring(14));
                } else if (lectura.startsWith("salir")) {
                    agregar(lectura.substring(5) + "\n");
                    cerrar();
                    return;
                } else if (lectura.startsWith("mensaje")) {
                    agregar(lectura.substring(7) + "\n");
                }
                scrollAreaTexto.validate();

            }

        }

        private void cerrar() {
            try {
                socket.close();
            } catch (Exception ex) {
            }
        }

        private void agregar(String texto) {
            areaTexto.append(texto);
            scrollAreaTexto.getVerticalScrollBar().setValue(scrollAreaTexto.getVerticalScrollBar().getMaximum());
        }

    }

}
