import pandas as pd
import os

def process_results():
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    input_file = os.path.join(base_dir, 'results/milestone2/experiment_raw.csv')
    output_file = os.path.join(base_dir, 'results/milestone2/experiment_processed.csv')

    print(f"Lettura dei dati da: {input_file}")

    try:
        df = pd.read_csv(input_file)
    except FileNotFoundError:
        print(f"Errore: Il file {input_file} non esiste.")
        return

    # --- IL FIX FONDAMENTALE È QUI ---
    # Ripristiniamo la parola "None" laddove Pandas aveva inserito un valore vuoto (NaN)
    df['Balancing'] = df['Balancing'].fillna('None')
    df['FeatureSelection'] = df['FeatureSelection'].fillna('None')

    # 1. Raggruppa e calcola le medie
    # Aggiungiamo dropna=False per vietare a Pandas di cancellare configurazioni
    group_cols = ['Dataset', 'Classifier', 'Balancing', 'FeatureSelection']
    metric_cols = ['Precision', 'Recall', 'AUC', 'Kappa', 'NPofB20']

    summary_df = df.groupby(group_cols, dropna=False)[metric_cols].mean().reset_index()

    # 2. Rinomina le colonne
    summary_df.rename(columns={
        'Dataset': 'projName',
        'Classifier': 'classifier',
        'Balancing': 'balancing',
        'FeatureSelection': 'featureSelection',
        'Precision': 'precision',
        'Recall': 'recall',
        'AUC': 'ROC_Area',
        'Kappa': 'kappa',
        'NPofB20': 'NPofB20'
    }, inplace=True)

    # 3. Calcolo F-Measure standard
    summary_df['fMeasure'] = (2 * summary_df['precision'] * summary_df['recall']) / \
                             (summary_df['precision'] + summary_df['recall'])
    summary_df['fMeasure'] = summary_df['fMeasure'].fillna(0)

    # 4. Aggiungo Sensitivity e TP_Rate
    summary_df['sensitivity'] = summary_df['recall']
    summary_df['TP_Rate'] = summary_df['recall']

    # 5. Arrotondo tutto a 3 decimali
    cols_to_decimal = ['sensitivity', 'TP_Rate', 'precision', 'recall', 'fMeasure', 'ROC_Area', 'kappa', 'NPofB20']
    for col in cols_to_decimal:
        summary_df[col] = summary_df[col].round(3)

    # 6. Riordino le colonne esattamente come l'esempio del professore
    final_columns = [
        'projName', 'classifier', 'balancing', 'featureSelection',
        'sensitivity', 'TP_Rate', 'precision', 'recall', 'fMeasure', 'ROC_Area', 'kappa', 'NPofB20'
    ]
    summary_df = summary_df[final_columns]

    # 7. Salvataggio formattato per Excel ITA
    summary_df.to_csv(output_file, index=False, sep=';', decimal=',')
    print(f"Fatto! Risultati COMPLETI salvati in: {output_file}")

if __name__ == "__main__":
    process_results()