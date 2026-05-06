package si2026.alejandrodelpozoalu.p03;

import core.game.Observation;
import core.game.StateObservation;
import core.player.AbstractPlayer;
import ontology.Types.ACTIONS;
import tools.ElapsedCpuTimer;
import tools.Vector2d;
import java.util.*;

public class Practica_03 extends AbstractPlayer {
    int bloque;
    int columnas, filas;
    int[][] grid;
    int[][] idsIslas;
    Set<String> trampolinesInseguros = new HashSet<>();

    final int EMPTY = 0, WALL = 1, WATER = 2, GOAL = 3, TRAMPOLINE = 4, LANDING = 5, SPAWNER = 6;

    public Practica_03(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {
        bloque = stateObs.getBlockSize();
        filas = stateObs.getWorldDimension().height / bloque;
        columnas = stateObs.getWorldDimension().width / bloque;
    }

    @Override
    public ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {
        analizarMapa(stateObs);
        identificarIslas();
        
        Vector2d posAvatar = stateObs.getAvatarPosition();
        int x = Math.max(0, Math.min(columnas - 1, (int) (posAvatar.x / bloque)));
        int y = Math.max(0, Math.min(filas - 1, (int) (posAvatar.y / bloque)));
        
        int islaActual = idsIslas[x][y];
        int[] metaG = encontrarMetaGlobal();

        // --- 1. PRIORIDAD: DESEMBARCO A ISLA O META ---
        ACTIONS[] acciones = {ACTIONS.ACTION_DOWN, ACTIONS.ACTION_RIGHT, ACTIONS.ACTION_LEFT, ACTIONS.ACTION_UP};
        int[][] dirs = {{0, 1}, {1, 0}, {-1, 0}, {0, -1}};

        for (int i = 0; i < 4; i++) {
            int nx = x + dirs[i][0];
            int ny = y + dirs[i][1];
            if (nx >= 0 && nx < columnas && ny >= 0 && ny < filas) {
                // Si estamos en el agua/nenúfar y al lado hay TIERRA FIRME (isla, landing o meta)
                if (islaActual == -1 && (idsIslas[nx][ny] != -1 || grid[nx][ny] == GOAL)) {
                    return acciones[i];
                }
            }
        }

        // --- 2. SALTO A NENÚFAR O TRAMPOLÍN SEGURO ---
        if (metaG != null) {
            for (int i = 0; i < 4; i++) {
                int nx = x + dirs[i][0];
                int ny = y + dirs[i][1];
                if (nx >= 0 && nx < columnas && ny >= 0 && ny < filas) {
                    
                    // Si hay un trampolín, verificar seguridad antes de pisar
                	if (grid[nx][ny] == TRAMPOLINE) {
                	    // Doble verificación: solo si estamos en tierra firme Y es seguro
                	    if (islaActual != -1 && !trampolinesInseguros.contains(nx + "," + ny)
                	            && esTrampolinSeguro(stateObs, nx, ny)) {
                	        return acciones[i];
                	    }
                	    continue; // Nunca pisar un trampolín inseguro
                	}

                    // Si hay madera física y nos acerca a la meta
                    if (hayMaderaFisica(stateObs, nx, ny)) {
                        if (ny > y || (ny == y && Math.abs(nx - metaG[0]) < Math.abs(x - metaG[0]))) {
                            return acciones[i];
                        }
                    }
                }
            }
        }

        // --- 3. MOVIMIENTO POR TIERRA ---
        if (islaActual != -1) {
            ACTIONS movHaciaOrilla = buscarCaminoAOrilla(x, y, islaActual);
            if (movHaciaOrilla != ACTIONS.ACTION_NIL) return movHaciaOrilla;
        }

        return ACTIONS.ACTION_NIL;
    }

    private boolean esTrampolinSeguro(StateObservation stateObs, int tx, int ty) {
        // Si ya sabemos que es inseguro, no recalcular
        String clave = tx + "," + ty;
        if (trampolinesInseguros.contains(clave)) return false;

        ArrayList<Observation>[][] obs = stateObs.getObservationGrid();

        // --- 1. Detectar itype del trampolín ---
        int itype = -1;
        for (Observation o : obs[tx][ty]) {
            if (o.itype >= 14 && o.itype <= 17) {
                itype = o.itype;
                break;
            }
        }
        if (itype == -1) return false;

        // --- 2. Dirección del salto ---
        int dx = 0, dy = 0;
        if      (itype == 14) dx =  1;
        else if (itype == 15) dx = -1;
        else if (itype == 16) dy = -1;
        else if (itype == 17) dy =  1;

        // --- 3. Probar distancias de salto 1..4 para encontrar dónde aterriza ---
        //    El primer suelo sólido que encuentre es el aterrizaje real
        boolean encontrado = false;
        for (int dist = 1; dist <= 4; dist++) {
            int landX = tx + dx * dist;
            int landY = ty + dy * dist;

            if (landX < 0 || landX >= columnas || landY < 0 || landY >= filas) break;

            int celda = grid[landX][landY];

            // Si hay pared o suelo: aquí aterriza
            if (celda == WALL || celda == EMPTY || celda == LANDING || celda == GOAL) {
                // Es seguro solo si es tierra firme o meta (no pared ni agua)
                boolean seguro = (celda == EMPTY || celda == LANDING || celda == GOAL)
                                  && idsIslas[landX][landY] != -1;
                if (!seguro) trampolinesInseguros.add(clave);
                return seguro;
            }

            // Si en algún punto intermedio hay agua pura → inseguro
            if (celda == WATER) {
                trampolinesInseguros.add(clave);
                return false;
            }
        }

        // Si no encontró suelo → inseguro
        trampolinesInseguros.add(clave);
        return false;
    }

    private boolean hayMaderaFisica(StateObservation stateObs, int gx, int gy) {
        ArrayList<Observation>[][] obs = stateObs.getObservationGrid();
        for (Observation o : obs[gx][gy]) {
            if (o.itype != 3 && o.itype != 0 && o.category != 0 && o.category != 4 && o.category != 2) {
                Vector2d centro = new Vector2d(gx * bloque + bloque/2.0, gy * bloque + bloque/2.0);
                if (o.position.dist(centro) < (bloque * 0.6)) return true;
            }
        }
        return false;
    }

    private ACTIONS buscarCaminoAOrilla(int x, int y, int idIsla) {
        int targetX = -1, targetY = -1, maxYaEncontrada = -1;
        for (int i = 0; i < columnas; i++) {
            for (int j = 0; j < filas; j++) {
                if (idsIslas[i][j] == idIsla && esOrilla(i, j)) {
                    if (j > maxYaEncontrada) {
                        maxYaEncontrada = j; targetX = i; targetY = j;
                    }
                }
            }
        }
        if (targetX != -1) {
            if (x == targetX && y == targetY) return ACTIONS.ACTION_NIL;
            List<ACTIONS> ruta = pathfind(x, y, targetX, targetY, idIsla);
            if (ruta != null && !ruta.isEmpty()) return ruta.get(0);
        }
        return ACTIONS.ACTION_NIL;
    }

    private boolean esOrilla(int x, int y) {
        int[][] d = {{0,1},{0,-1},{1,0},{-1,0}};
        for(int[] dir : d) {
            int nx = x + dir[0], ny = y + dir[1];
            if (nx >= 0 && nx < columnas && ny >= 0 && ny < filas) {
                if (grid[nx][ny] == WATER || grid[nx][ny] == SPAWNER) return true;
            }
        }
        return false;
    }

    private void analizarMapa(StateObservation stateObs) {
        this.grid = new int[columnas][filas];
        ArrayList<Observation>[][] obs = stateObs.getObservationGrid();
        for (int i = 0; i < columnas; i++) {
            for (int j = 0; j < filas; j++) {
                if (!obs[i][j].isEmpty()) {
                    Observation o = obs[i][j].get(0);
                    if (o.category == 2) grid[i][j] = GOAL;
                    else if (o.category == 6) grid[i][j] = LANDING;
                    else if (o.category == 4) {
                        if (o.itype == 0) grid[i][j] = WALL;
                        else if (o.itype == 3) grid[i][j] = WATER;
                        else if (o.itype == 7 || o.itype == 8) grid[i][j] = SPAWNER;
                        else if (o.itype >= 14 && o.itype <= 17) grid[i][j] = TRAMPOLINE;
                    }
                }
            }
        }
    }

    private void identificarIslas() {
        idsIslas = new int[columnas][filas];
        for(int[] r : idsIslas) Arrays.fill(r, -1);
        int count = 0;
        for (int i = 0; i < columnas; i++) {
            for (int j = 0; j < filas; j++) {
                // Consideramos tierra firme: Vacío, Plataforma o Trampolín
                if (idsIslas[i][j] == -1 && (grid[i][j] == EMPTY || grid[i][j] == LANDING || grid[i][j] == TRAMPOLINE)) {
                    bfs(i, j, count++);
                }
            }
        }
    }

    private void bfs(int x, int y, int id) {
        Queue<int[]> q = new LinkedList<>();
        q.add(new int[]{x, y});
        idsIslas[x][y] = id;
        int[][] d = {{0,1},{0,-1},{1,0},{-1,0}};
        while(!q.isEmpty()){
            int[] p = q.poll();
            for(int[] dir : d){
                int nx=p[0]+dir[0], ny=p[1]+dir[1];
                if(nx>=0 && nx<columnas && ny>=0 && ny<filas && idsIslas[nx][ny]==-1 
                   && (grid[nx][ny] == EMPTY || grid[nx][ny] == LANDING || grid[nx][ny] == TRAMPOLINE)){
                    idsIslas[nx][ny]=id; q.add(new int[]{nx, ny});
                }
            }
        }
    }

    private List<ACTIONS> pathfind(int x1, int y1, int x2, int y2, int idIsla) {
        PriorityQueue<Node> open = new PriorityQueue<>();
        Map<String, Integer> dists = new HashMap<>();
        open.add(new Node(x1, y1, 0, Math.abs(x1-x2)+Math.abs(y1-y2), null, null));
        while(!open.isEmpty()){
            Node curr = open.poll();
            if(curr.x == x2 && curr.y == y2) return reconstruct(curr);
            String key = curr.x+","+curr.y;
            if(dists.containsKey(key) && dists.get(key) <= curr.g) continue;
            dists.put(key, curr.g);
            int[][] moves = {{0,-1},{0,1},{-1,0},{1,0}};
            ACTIONS[] acts = {ACTIONS.ACTION_UP, ACTIONS.ACTION_DOWN, ACTIONS.ACTION_LEFT, ACTIONS.ACTION_RIGHT};
            for(int i=0; i<4; i++){
                int nx = curr.x+moves[i][0], ny = curr.y+moves[i][1];
                if(nx>=0 && nx<columnas && ny>=0 && ny<filas && idsIslas[nx][ny] == idIsla){
                    open.add(new Node(nx, ny, curr.g+1, Math.abs(nx-x2)+Math.abs(ny-y2), curr, acts[i]));
                }
            }
        }
        return null;
    }

    private List<ACTIONS> reconstruct(Node n) {
        List<ACTIONS> res = new ArrayList<>();
        while(n.parent != null) { res.add(0, n.act); n = n.parent; }
        return res;
    }

    private int[] encontrarMetaGlobal() {
        for(int i=0; i<columnas; i++) for(int j=0; j<filas; j++) if(grid[i][j] == GOAL) return new int[]{i,j};
        return null;
    }

    private class Node implements Comparable<Node> {
        int x, y, g, f; Node parent; ACTIONS act;
        Node(int x, int y, int g, int h, Node p, ACTIONS a){
            this.x=x; this.y=y; this.g=g; this.f=g+h; this.parent=p; this.act=a;
        }
        public int compareTo(Node o) { return Integer.compare(this.f, o.f); }
    }
}


/*
		System.out.println("Mapa: [" + columnas + " x " + filas + "]");
        ArrayList<Observation>[][] mapa = stateObs.getObservationGrid();
        for(int i = 0; i< filas; i++) {
			String linea = "";
			for(int j = 0; j<columnas; j++) {
				if(mapa[j][i].size() > 0) {
					int categoria = mapa[j][i].get(0).category;
	                
	                if (categoria == 0) linea += "Y ";
	                else if (categoria == 2) {
	                	salida[0] = j;
	                	salida[1] = i;
	                	linea += "G ";
	                }
	                else if (categoria == 4) {
	                	int tipo = mapa[j][i].get(0).itype;
	                	if(tipo == 0) {
		                	linea += "W ";
	                	}
	                	else if(tipo == 3) {
	                		linea += "A ";
	                	}
	                	else if(tipo == 7) {
	                		linea += "Si";
	                	}
	                	else if(tipo == 8) {
	                		linea += "Sd";
	                	}
	                	else if(tipo == 14) {
	                		linea += "Tb";
	                	}
	                	else if(tipo == 15) {
	                		linea += "Ta";
	                	}
	                	else if(tipo == 16) {
	                		linea += "Td";
	                	}
	                	else if(tipo == 17) {
	                		linea += "Ti";
	                	}
	                	else linea += tipo + "U";
	                }
	                else if (categoria == 6) linea += "P ";
	                else linea += categoria + "O";
				} else {
					linea += ". ";
				}
			}
			System.out.println(linea);
        }
*/
