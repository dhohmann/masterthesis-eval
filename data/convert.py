import matplotlib.pyplot as plt
import pandas as pds
import csv
import os
import statistics

import sys
import getopt


def main(argv):
    inputfile = ''
    try:
        opts, args = getopt.getopt(argv, "hi:", ["input=", 'help'])
    except getopt.GetoptError:
        print ('test.py -i <inputfile> -o <outputfile>')
        sys.exit(2)
    for opt, arg in opts:
        if  opt in ("-h", "--help"):
            print ('convert.py -i <inputfile>')
            sys.exit()
        elif opt in ("-i", "--ifile"):
            inputfile = arg
        else:
            print('Unrecognized command line argument')
            sys.exit(2)
    
    if(inputfile == ''):
        print('No input file given')
        sys.exit(2)

    inputfile = os.path.abspath(inputfile)
    if os.path.exists(inputfile) == False:
        print('File does not exist')
        sys.exit(2)
    pathname, extension = os.path.splitext(inputfile)
    path = os.path.join(os.path.dirname(__file__), pathname.split('/')[-1])
    
    os.makedirs(path, exist_ok=True)

    accumulated = []
    model_info = []
    raw = []


    with open(inputfile, 'r') as csvfile:
        name = ''
        creation = []
        hasSolution = []
        model_data = []
        data = []
        for row in csv.DictReader(csvfile, delimiter=';'):
            if name != row['model']:
                if(len(creation) > 0):
                    data.append(name)
                    data.append(len(creation))
                    data.append(statistics.mean(creation))
                    data.append(statistics.median(creation))
                    data.append(statistics.mean(hasSolution))
                    data.append(statistics.median(hasSolution))
                    accumulated.append(data)

                name = row['model']
                model_data = []
                data = []
                creation = []
                hasSolution = []
                model_data.append(name)
                model_data.append(int(row['features']))
                model_data.append(int(row['literals']))
                model_data.append(int(row['attributes']))
                model_data.append(int(row['variables']))
                model_data.append(int(row['variables for aggregations']))
                model_data.append(int(row['configuring constraints']))
                model_info.append(model_data)

            raw.append([name, int(row['creation']), int(row['hasSolution'])])
            creation.append(int(row['creation']))
            hasSolution.append(int(row['hasSolution']))

        if(len(creation) > 0):
            data.append(name)
            data.append(len(creation))
            data.append(statistics.mean(creation))
            data.append(statistics.median(creation))
            data.append(statistics.mean(hasSolution))
            data.append(statistics.median(hasSolution))
            accumulated.append(data)

    modelFile = os.path.join(path, 'model.csv')
    modelFileTex = os.path.join(path, 'model.tex')
    dataFile = os.path.join(path, 'data.csv')
    dataFileTex = os.path.join(path, 'data.tex')
    rawFile = os.path.join(path, 'raw.csv')
    rawFileTex = os.path.join(path, 'raw.tex')
    rawFilePdf = os.path.join(path, 'raw.pdf')

    with open(modelFile, 'w', newline='') as csvfile:
        writer = csv.writer(csvfile, delimiter=';')
        writer.writerow(['model', 'features', 'literals', 'attributes',
                        'variables', 'variables for aggregations', 'configuring constraints'])
        writer.writerows(model_info)

    with open(dataFile, 'w', newline='') as csvfile:
        writer = csv.writer(csvfile, delimiter=';')
        writer.writerow(['model', 'iterations', 'avg creation',
                        'mean creation', 'avg solution', 'mean solution'])
        writer.writerows(accumulated)

    with open(rawFile, 'w', newline='') as csvfile:
        writer = csv.writer(csvfile, delimiter=';')
        writer.writerow(['model', 'creation', 'solution'])
        writer.writerows(raw)


    # Plot raw as scatter
    dataFrameModel = pds.read_csv(modelFile, delimiter=';')
    dataFrameModel.to_latex(modelFileTex, index=False,
                            caption='Verwendete Modelle')
    dataFrameData = pds.read_csv(dataFile, delimiter=';')
    dataFrameData.to_latex(dataFileTex, index=False,
                        caption='Ergebnisse der Messungen')
    dataFrameRaw = pds.read_csv(rawFile, delimiter=';')
    dataFrameRaw.to_latex(rawFileTex, index=False,  caption='Messwerte')


    plt.rcParams["figure.autolayout"] = True

    headers = ['model', 'creation', 'solution']
    df = pds.read_csv(rawFile, delimiter=';')


    plt.scatter(df.model, df.solution, label='Erfüllbarkeit')
    plt.scatter(df.model, df.creation, label='Conversion')

    plt.title('Ausführungszeiten verschiedener Modelle')
    plt.xlabel('Modelle')
    plt.ylabel('Zeit [ms]')
    plt.legend(title='Operationen')
    plt.xticks(rotation=90)
    plt.savefig(rawFilePdf, format='pdf')



if __name__ == "__main__":
    main(sys.argv[1:])


