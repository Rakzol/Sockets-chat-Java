package servidorchat;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UTFDataFormatException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * @author Carlos Gilberto Jaime Rosas.
 */
public class Principal {

    private static ServerSocket servidor;
    private static ExecutorService hilos;

    private static HashMap<String, DataOutputStream> clientes;
    private static HashMap<String, String> usuarios;
    private static HashMap<String, ArrayList<String>> bloqueos;

    public static void main(String[] args) throws Exception {

        /*
         * Vlidamos que se ingresen los argumentos necesarios.
         */
        if (args.length < 2) {
            System.err.println("No se agregó la cantidad de argumentos necesarios:\n"
                    + "1 .- Número de puero del servidor en el rango de 0 a 65535.\n"
                    + "2 .- La cantidad de hilos o clientes conectados al mismo tiempo.");
            System.exit(1);
        }

        /* 
         * Inicializamos la variable usuarios desde el valor que tiene en el
         * archivo o con una nueva instancia, dependiendo de si el archivo
         * existe o no.
         */
        File archivoUsuarios = new File("usuarios");
        if (archivoUsuarios.isDirectory()) {
            System.err.println("La carpeta " + archivoUsuarios.getName() + " no se puede llamar igual que el archivo.");
            System.exit(1);
        }
        try {
            if (archivoUsuarios.exists()) {
                ObjectInputStream entradaArchivoUsuarios = new ObjectInputStream(new FileInputStream(archivoUsuarios));
                usuarios = (HashMap<String, String>) entradaArchivoUsuarios.readObject();
                entradaArchivoUsuarios.close();
            } else {
                usuarios = new HashMap<String, String>();
                actualizarArchivoUsuarios();
            }
        } catch (FileNotFoundException ex) {
            System.err.println("No tiene control total en el archivo: " + archivoUsuarios.getName());
            System.exit(1);
        } catch (Exception ex) {
            System.err.println("El archivo: " + archivoUsuarios.getName() + " está corrupto.");
            System.exit(1);
        }

        /* 
         * Inicializamos la variable bloqueos desde el valor que tiene en el
         * archivo o con una nueva instancia, dependiendo de si el archivo
         * existe o no.
         */
        File archivoBloqueos = new File("bloqueos");
        if (archivoBloqueos.isDirectory()) {
            System.err.println("La carpeta " + archivoBloqueos.getName() + " no se puede llamar igual que el archivo.");
            System.exit(1);
        }
        try {
            if (archivoBloqueos.exists()) {
                ObjectInputStream entradaArchivoBloqueos = new ObjectInputStream(new FileInputStream(archivoBloqueos));
                bloqueos = (HashMap<String, ArrayList<String>>) entradaArchivoBloqueos.readObject();
                entradaArchivoBloqueos.close();
            } else {
                bloqueos = new HashMap<String, ArrayList<String>>();
                actualizarArchivoBloqueos();
            }
        } catch (FileNotFoundException ex) {
            System.err.println("No tiene control total en el archivo: " + archivoBloqueos.getName());
            System.exit(1);
        } catch (Exception ex) {
            System.err.println("El archivo: " + archivoBloqueos.getName() + " está corrupto.");
            System.exit(1);
        }

        clientes = new HashMap<>();

        try {
            hilos = Executors.newFixedThreadPool(Integer.valueOf(args[1]));
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.err.println("No se agregó la cantidad de hilos.");
            System.exit(1);
        } catch (NumberFormatException ex) {
            System.err.println("La cantidad de hilos tiene que ser un valor entero.");
            System.exit(1);
        } catch (IllegalArgumentException ex) {
            System.err.println("La cantidad de hilos tiene que ser mayor a 0.");
            System.exit(1);
        } catch (Exception ex) {
            System.err.println("No se puede crear la cantidad de hilos ingresada.");
            System.exit(1);
        }

        try {
            servidor = new ServerSocket(Integer.valueOf(args[0]));
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.err.println("No se agregó el número de puerto.");
            System.exit(1);
        } catch (NumberFormatException ex) {
            System.err.println("El número de puerto tiene que ser un valor entero.");
            System.exit(1);
        } catch (IllegalArgumentException ex) {
            System.err.println("El número de puerto tiene que estar en el rango de 0 a 65535.");
            System.exit(1);
        } catch (BindException ex) {
            System.err.println("El número de puerto ocupado.");
            System.exit(1);
        } catch (IOException ex) {
            System.err.println("No se puede crear el servidor.");
            System.exit(1);
        }

        while (true) {
            try {
                hilos.execute(new Cliente(servidor.accept()));
            } catch (RejectedExecutionException ex) {
                System.err.println("No se pude ejecutar el hilo.");
            } catch (Exception ex) {
                System.err.println("No se pude aceptar la conexión.");
            }
        }
    }

    private static class Cliente implements Runnable {

        private Socket socket;
        private String nombre;
        private DataInputStream lector;
        private DataOutputStream escritor;

        private Cliente(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {

            try {
                lector = new DataInputStream(socket.getInputStream());
                escritor = new DataOutputStream(socket.getOutputStream());
            } catch (Exception ex) {
            }

            while (true) {
                String contrasena;
                try {
                    escritor.writeUTF("mensajeIngrese un usuario:");
                    nombre = lector.readUTF().substring(7).toLowerCase();
                    escritor.writeUTF("mensajeIngrese una contraseña:");
                    contrasena = lector.readUTF().substring(7).toLowerCase();
                } catch (UTFDataFormatException ex) {
                    nombre = "";
                    contrasena = "";
                } catch (Exception ex) {
                    System.err.println("Se perdio la conexión con el cliente.");
                    cerrar();
                    return;
                }

                if (nombre.equals("/salir") || contrasena.equals("/salir")) {
                    cerrar();
                    return;
                }

                synchronized (clientes) {
                    synchronized (usuarios) {
                        synchronized (bloqueos) {
                            String errores = "";
                            if (clientes.containsKey(nombre)) {
                                errores += "\nEl usuario ya esta conectado.";
                            }
                            if (nombre.equals("servidor") || nombre.equals("todos")) {
                                errores += "\n" + nombre + " es una palabra reservada.";
                            }
                            if (nombre.contains(" ")) {
                                errores += "\nEl nombre no puede contener espacios.";
                            }
                            if (nombre.length() < 1) {
                                errores += "\nEl nombre tiene que tener mínimo 1 caracter";
                            }
                            if (nombre.length() > 16) {
                                errores += "\nEl nombre tiene queter un máximo de 16 caracteres.";
                            }
                            if (errores.length() == 0) {
                                if (usuarios.containsKey(nombre)) {
                                    if (usuarios.get(nombre).equals(contrasena)) {
                                        clientes.put(nombre, escritor);
                                    } else {
                                        errores += "\nLa contraseña es incorrecta.";
                                    }
                                } else {
                                    clientes.put(nombre, escritor);
                                    usuarios.put(nombre, contrasena);
                                    bloqueos.put(nombre, new ArrayList<String>());
                                    actualizarArchivoUsuarios();
                                    actualizarArchivoBloqueos();
                                }
                            }
                            if (errores.length() == 0) {
                                mensaje("nombreAceptado" + nombre);
                                mensaje("mensajeClientes conectados:" + clientes());
                                mensajeGlobal("mensajeservidor -> todos: " + nombre + " conectado.");
                                break;
                            } else {
                                try {
                                    escritor.writeUTF("mensajeLas credenciales no son válidas:" + errores);
                                } catch (Exception ex) {
                                }
                            }
                        }
                    }
                }
            }

            while (true) {
                String lectura;
                try {
                    lectura = lector.readUTF();
                } catch (UTFDataFormatException ex) {
                    lectura = "";
                } catch (Exception ex) {
                    System.err.println(nombre + ": Se perdio la conexión con el cliente.");
                    desconectar();
                    return;
                }

                if (lectura.startsWith("mensaje")) {
                    mensajePublico(lectura.substring(7));
                } else if (lectura.equalsIgnoreCase("/salir")) {
                    desconectar();
                    return;
                } else if (lectura.equalsIgnoreCase("/ayuda")) {
                    mensaje("mensajeLista de comandos:" + ayuda());
                } else if (lectura.equalsIgnoreCase("/clientes")) {
                    mensaje("mensajeClientes conectados:" + clientes());
                } else if (lectura.equalsIgnoreCase("/bloqueados")) {
                    mensaje("mensajeUsuarios bloqueados:" + bloqueados());
                } else if (lectura.toLowerCase().startsWith("/bloquear")) {
                    if (lectura.length() > 9) {
                        bloquear(lectura.substring(10).toLowerCase());
                    } else {
                        mensaje("mensaje/bloquear USUARIO -> Bloquea al USUARIO.");
                    }
                } else if (lectura.toLowerCase().startsWith("/desbloquear")) {
                    if (lectura.length() > 12) {
                        desbloquear(lectura.substring(13).toLowerCase());
                    } else {
                        mensaje("mensaje/desbloquear USUARIO -> Desbloquea al USUARIO.");
                    }
                } else if (lectura.toLowerCase().startsWith("/mensaje")) {
                    if (lectura.length() > 8) {
                        mensajePrivado(lectura.substring(9));
                    } else {
                        mensaje("mensaje/mensaje USUARIO MENSAJE -> Manda el MENSAJE privadamente a USUARIO.");
                    }
                } else {
                    mensaje("mensajeComando " + lectura + " desconocido.");
                }

            }
        }

        /**
         * Cierra la conexión del cliente actual mandandole un mensaje de cierre
         * de conexion y cerrando en socket.
         */
        private void cerrar() {
            try {
                escritor.writeUTF("salirConexión cerrada.");
            } catch (Exception ex) {
            }
            try {
                socket.close();
            } catch (Exception ex) {
            }
        }

        /**
         * Cierra la conexión del cliente actual y se lo indica a todos los
         * clientes mandando un mensaje global.
         */
        private void desconectar() {
            synchronized (clientes) {
                cerrar();
                clientes.remove(nombre);
                mensajeGlobal("mensajeservidor -> todos: " + nombre + " desconectado.");
            }
        }

        /**
         * Envia una mensaje al cliente actual pasado por argumento.
         *
         * @param mensaje String que contiene el mensaje a enviar.
         */
        private void mensaje(String mensaje) {
            synchronized (clientes) {
                try {
                    clientes.get(nombre).writeUTF(mensaje);
                } catch (Exception ex) {
                }
            }
        }

        /**
         * Envia una mensaje al cliente actual y un segundo usuario pasados por
         * argumento, si el segundo usuario no esta conectado se le manda un
         * mensaje al cliente actual informandoselo.
         *
         * @param nombrePar String que contiene el nombre del usuarioa a enviar
         * el mensaje.
         * @param mensaje String que contiene el mensaje a enviar.
         */
        private void mensajePar(String nombrePar, String mensaje) {
            synchronized (clientes) {
                synchronized (bloqueos) {
                    if (!clientes.containsKey(nombrePar)) {
                        mensaje("mensaje" + nombrePar + " no está conectado.");
                        return;
                    }
                    mensaje(mensaje);
                    if (!bloqueos.get(nombrePar).contains(nombre)) {
                        try {
                            clientes.get(nombrePar).writeUTF(mensaje);
                        } catch (IOException ex) {
                        }
                    }
                }
            }
        }

        /**
         * Envia una mensaje al cliente actual y un segundo usuario pasados por
         * argumento, si el segundo usuario no esta conectado o el usuario o
         * mensaje son invalidos se le manda un mensaje al cliente actual
         * informandoselo.
         *
         * @param datos String que contiene el nombre del usuarioa a enviar el
         * mensaje y el mensaje a enviar.
         */
        private void mensajePrivado(String datos) {
            String nombrePar = datos.indexOf(" ") > 0 && datos.indexOf(" ") < 17 ? datos.substring(0, datos.indexOf(" ")).toLowerCase() : "";
            if (nombrePar.length() > 16 || nombrePar.length() < 1) {
                mensaje("mensajeIngrese un nombre sin espacios y no mayor a 16 caracteres para mandar el mensaje.");
                return;
            }
            String mensaje = datos.substring(nombrePar.length() + 1);
            if (mensaje.startsWith(" ") || mensaje.endsWith(" ") || mensaje.length() < 1) {
                mensaje("mensajeIngrese un mensaje que tenga por lo menos un caracter y que no inicie ni termine con espacios.");
                return;
            }
            mensajePar(nombrePar, "mensaje" + nombre + " -> " + nombrePar + ": " + mensaje);
        }

        /**
         * Envia una mensaje pasado por argumento a todos los clientes
         * conectados.
         *
         * @param mensaje String con el mensaje que se mandara a los clientes.
         */
        private void mensajeGlobal(String mensaje) {
            synchronized (clientes) {
                synchronized (bloqueos) {
                    clientes.forEach((nombrePar, escritorPar) -> {
                        if (!bloqueos.get(nombrePar).contains(nombre)) {
                            try {
                                escritorPar.writeUTF(mensaje);
                            } catch (Exception ex) {
                            }
                        }
                    });
                }
            }
        }

        /**
         * Valida y Envia una mensaje pasado por argumento a todos los clientes
         * conectados.
         *
         * @param mensaje String con el mensaje que se mandara a los clientes.
         */
        private void mensajePublico(String mensaje) {
            if (mensaje.startsWith(" ") || mensaje.endsWith(" ") || mensaje.length() < 1) {
                mensaje("mensajeIngrese un mensaje que tenga por lo menos un caracter y que no inicie ni termine con espacios.");
                return;
            }
            mensajeGlobal("mensaje" + nombre + " -> todos: " + mensaje);
        }

        /**
         * Regresa un string con la descripción de todos los comandos.
         *
         * @return String con la descripción de todos los comandos.
         */
        private String ayuda() {
            return "\n/ayuda -> Muestra la descripción de todos los comandos."
                    + "\n/mensaje USUARIO MENSAJE -> Manda el MENSAJE privadamente a USUARIO."
                    + "\n/bloquear USUARIO -> Bloquea al USUARIO"
                    + "\n/desbloquear USUARIO -> desbloquea al USUARIO"
                    + "\n/salir -> Cierra la conexión con el servidor."
                    + "\n/clientes -> Muestra una lista de los clientes conectados."
                    + "\n/bloqueados -> Muestra una lista de los usuarios bloqueados.";
        }

        /**
         * Regresa un string con los nombres de todos los clientes conectados.
         *
         * @return String con los nombres de todos los clientes conectados.
         */
        private String clientes() {
            synchronized (clientes) {
                String listaClientes = "";
                for (Entry<String, DataOutputStream> cliente : clientes.entrySet()) {
                    listaClientes += "\n" + cliente.getKey();
                }
                return listaClientes;
            }
        }

        /**
         * Regresa un string con todos los usuarios bloqueados por este cliente.
         *
         * @return String con todos los usuarios bloqueados por este cliente.
         */
        private String bloqueados() {
            synchronized (bloqueos) {
                String listaBloqueos = "";
                for (String bloqueado : bloqueos.get(nombre)) {
                    listaBloqueos += "\n" + bloqueado;
                }
                return listaBloqueos;
            }
        }

        /**
         * Bloquea el usuario pasado por argumento para el cliente actual.
         *
         * @param nombrePar El nombre del usuario a Bloquear.
         */
        private void bloquear(String nombrePar) {
            synchronized (bloqueos) {
                if (nombrePar.length() > 16 || nombrePar.length() < 1 || nombrePar.contains(" ")) {
                    mensaje("mensajeIngrese un nombre sin espacios y no mayor a 16 caracteres para bloquear.");
                    return;
                }
                if (nombrePar.equals(nombre)) {
                    mensaje("mensajeNo puedes bloquearte a ti mismo.");
                    return;
                }
                if (bloqueos.get(nombre).contains(nombrePar)) {
                    mensaje("mensaje" + nombrePar + " ya esta bloqueado.");
                    return;
                }
                bloqueos.get(nombre).add(nombrePar);
                actualizarArchivoBloqueos();
                mensaje("mensaje" + nombrePar + " bloqueado.");
            }
        }

        /**
         * Desbloquea el usuario pasado por argumento para el cliente actual.
         *
         * @param nombrePar El nombre del usuario a desbloquear.
         */
        private void desbloquear(String nombrePar) {
            synchronized (bloqueos) {
                if (nombrePar.length() > 16 || nombrePar.length() < 1 || nombrePar.contains(" ")) {
                    mensaje("mensajeIngrese un nombre sin espacios y no mayor a 16 caracteres para desbloquear.");
                    return;
                }
                if (nombrePar.equals(nombre)) {
                    mensaje("mensajeNo puedes desbloquearte a ti mismo.");
                    return;
                }
                if (!bloqueos.get(nombre).contains(nombrePar)) {
                    mensaje("mensaje" + nombrePar + " ya esta desbloqueado.");
                    return;
                }
                bloqueos.get(nombre).remove(nombrePar);
                actualizarArchivoBloqueos();
                mensaje("mensaje" + nombrePar + " desbloqueado.");
            }
        }
    }

    /**
     * Actualiza el archivo que almacena los usuarios.
     */
    private static void actualizarArchivoUsuarios() {
        synchronized (usuarios) {
            File archivoUsuarios = new File("usuarios");
            if (archivoUsuarios.isDirectory()) {
                System.err.println("La carpeta " + archivoUsuarios.getName() + " no se puede llamar igual que el archivo.");
                System.exit(1);
            }
            try {
                ObjectOutputStream salidaArchivoUsuarios = new ObjectOutputStream(new FileOutputStream(archivoUsuarios));
                salidaArchivoUsuarios.writeObject(usuarios);
                salidaArchivoUsuarios.close();
            } catch (FileNotFoundException ex) {
                System.err.println("No tiene control total en el archivo: " + archivoUsuarios.getName());
                System.exit(1);
            } catch (Exception ex) {
                System.err.println("El archivo: " + archivoUsuarios.getName() + " está corrupto.");
                System.exit(1);
            }
        }
    }

    /**
     * Actualiza el archivo que almacena los bloqueos.
     */
    private static void actualizarArchivoBloqueos() {
        synchronized (bloqueos) {
            File archivoBloqueos = new File("bloqueos");
            if (archivoBloqueos.isDirectory()) {
                System.err.println("La carpeta " + archivoBloqueos.getName() + " no se puede llamar igual que el archivo.");
                System.exit(1);
            }
            try {
                ObjectOutputStream salidaArchivoBloqueos = new ObjectOutputStream(new FileOutputStream(archivoBloqueos));
                salidaArchivoBloqueos.writeObject(bloqueos);
                salidaArchivoBloqueos.close();
            } catch (FileNotFoundException ex) {
                System.err.println("No tiene control total en el archivo: " + archivoBloqueos.getName());
                System.exit(1);
            } catch (Exception ex) {
                System.err.println("El archivo: " + archivoBloqueos.getName() + " está corrupto.");
                System.exit(1);
            }
        }
    }

}
